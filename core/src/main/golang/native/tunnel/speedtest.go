package tunnel

import (
	"context"
	"crypto/tls"
	"fmt"
	"hash/fnv"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"

	"github.com/Dreamacro/clash/adapter"
	"github.com/Dreamacro/clash/adapter/outboundgroup"
	C "github.com/Dreamacro/clash/constant"
	"github.com/Dreamacro/clash/tunnel"
)

const (
	speedTestConcurrency   = 32
	speedTestTotalTimeout  = 25 * time.Second
	speedTestNodeTimeout   = 8 * time.Second
	speedTestLatencyURL    = "https://www.gstatic.com/generate_204"
	speedTestDownloadURL   = "https://speed.cloudflare.com/__down?bytes=2097152"
	speedTestUploadURL     = "https://speed.cloudflare.com/__up"
	speedTestDownloadBytes = 2 * 1024 * 1024
	speedTestUploadBytes   = 1 * 1024 * 1024
)

type SpeedTestMode int

const (
	SpeedTestDownload SpeedTestMode = iota
	SpeedTestUpload
	SpeedTestDelay
)

type speedMetrics struct {
	Delay    int
	Download int64
	Upload   int64
	Error    string
}

var speedCache = struct {
	sync.Mutex
	generation uint64
	metrics    map[string]speedMetrics
}{
	metrics: make(map[string]speedMetrics),
}

var speedTestMutex sync.Mutex

func parseSpeedTestMode(value string) SpeedTestMode {
	switch strings.ToLower(strings.TrimSpace(value)) {
	case "upload":
		return SpeedTestUpload
	case "delay", "latency":
		return SpeedTestDelay
	default:
		return SpeedTestDownload
	}
}

func defaultSpeedMetrics() speedMetrics {
	return speedMetrics{Delay: -1, Download: -1, Upload: -1}
}

func allLeafProxies() []C.Proxy {
	global := tunnel.Proxies()["GLOBAL"]
	if global == nil {
		return nil
	}

	wrapped, ok := global.(*adapter.Proxy)
	if !ok {
		return nil
	}

	group, ok := wrapped.ProxyAdapter.(outboundgroup.ProxyGroup)
	if !ok {
		return nil
	}

	providers := group.Providers()
	if len(providers) == 0 {
		return nil
	}

	proxies := providers[0].Proxies()
	result := make([]C.Proxy, 0, len(proxies))
	seen := make(map[string]struct{}, len(proxies))

	for _, proxy := range proxies {
		switch proxy.Type() {
		case C.Direct, C.Reject:
			continue
		}

		if wrapped, ok := proxy.(*adapter.Proxy); ok {
			if _, isGroup := wrapped.ProxyAdapter.(outboundgroup.ProxyGroup); isGroup {
				continue
			}
		}

		if _, exists := seen[proxy.Name()]; exists {
			continue
		}

		seen[proxy.Name()] = struct{}{}
		result = append(result, proxy)
	}

	return result
}

func ensureSpeedCache(proxies []C.Proxy) {
	hash := fnv.New64a()
	for _, proxy := range proxies {
		_, _ = io.WriteString(hash, proxy.Name())
		_, _ = io.WriteString(hash, proxy.Type().String())
	}

	generation := hash.Sum64()

	speedCache.Lock()
	defer speedCache.Unlock()

	if speedCache.generation != generation {
		speedCache.generation = generation
		speedCache.metrics = make(map[string]speedMetrics)
	}
}

func getSpeedMetrics(name string) speedMetrics {
	speedCache.Lock()
	defer speedCache.Unlock()

	metrics, ok := speedCache.metrics[name]
	if !ok {
		return defaultSpeedMetrics()
	}

	return metrics
}

func updateSpeedMetrics(name string, mode SpeedTestMode, delay int, download, upload int64, testError error) {
	speedCache.Lock()
	defer speedCache.Unlock()

	metrics, ok := speedCache.metrics[name]
	if !ok {
		metrics = defaultSpeedMetrics()
	}

	switch mode {
	case SpeedTestDelay:
		metrics.Delay = delay
	case SpeedTestDownload:
		metrics.Delay = delay
		metrics.Download = download
	case SpeedTestUpload:
		metrics.Delay = delay
		metrics.Upload = upload
	}

	if testError != nil {
		metrics.Error = testError.Error()
	} else {
		metrics.Error = ""
	}

	speedCache.metrics[name] = metrics
}

func QueryAllProxies() *ProxyGroup {
	proxies := allLeafProxies()
	ensureSpeedCache(proxies)

	result := make([]*Proxy, 0, len(proxies))
	for _, proxy := range proxies {
		metrics := getSpeedMetrics(proxy.Name())

		result = append(result, &Proxy{
			Name:        proxy.Name(),
			Title:       proxy.Name(),
			Subtitle:    proxy.Type().String(),
			Type:        proxy.Type().String(),
			Delay:       metrics.Delay,
			Region:      detectRegion(proxy.Name()),
			Download:    metrics.Download,
			Upload:      metrics.Upload,
			Error:       metrics.Error,
			SpeedTested: true,
		})
	}

	return &ProxyGroup{
		Type:    C.Selector.String(),
		Now:     "",
		Proxies: result,
	}
}

func SpeedTest(modeValue string) *ProxyGroup {
	mode := parseSpeedTestMode(modeValue)

	speedTestMutex.Lock()
	defer speedTestMutex.Unlock()

	proxies := allLeafProxies()
	ensureSpeedCache(proxies)

	ctx, cancel := context.WithTimeout(context.Background(), speedTestTotalTimeout)
	defer cancel()

	semaphore := make(chan struct{}, speedTestConcurrency)
	var waitGroup sync.WaitGroup

	for _, proxy := range proxies {
		proxy := proxy

		waitGroup.Add(1)

		go func() {
			defer waitGroup.Done()

			select {
			case semaphore <- struct{}{}:
				defer func() { <-semaphore }()
			case <-ctx.Done():
				return
			}

			nodeCtx, nodeCancel := context.WithTimeout(ctx, speedTestNodeTimeout)
			defer nodeCancel()

			delay, download, upload, testError := testProxy(nodeCtx, proxy, mode)
			updateSpeedMetrics(proxy.Name(), mode, delay, download, upload, testError)
		}()
	}

	done := make(chan struct{})
	go func() {
		waitGroup.Wait()
		close(done)
	}()

	select {
	case <-done:
	case <-ctx.Done():
	}

	return QueryAllProxies()
}

func testProxy(ctx context.Context, proxy C.Proxy, mode SpeedTestMode) (delay int, download, upload int64, testError error) {
	delay = -1
	download = -1
	upload = -1

	if mode == SpeedTestDelay {
		value, err := proxy.URLTest(ctx, speedTestLatencyURL)
		if err == nil {
			delay = int(value)
		} else {
			testError = err
		}

		return
	}

	var waitGroup sync.WaitGroup
	waitGroup.Add(2)

	go func() {
		defer waitGroup.Done()

		value, err := proxy.URLTest(ctx, speedTestLatencyURL)
		if err == nil {
			delay = int(value)
		}
	}()

	go func() {
		defer waitGroup.Done()

		switch mode {
		case SpeedTestDownload:
			download, testError = testDownload(ctx, proxy)
		case SpeedTestUpload:
			upload, testError = testUpload(ctx, proxy)
		}
	}()

	waitGroup.Wait()

	return
}

func testDownload(ctx context.Context, proxy C.Proxy) (int64, error) {
	client, cleanup, err := createProxyClient(ctx, proxy, speedTestDownloadURL)
	if err != nil {
		return -1, err
	}
	defer cleanup()

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, speedTestDownloadURL, nil)
	if err != nil {
		return -1, err
	}

	start := time.Now()
	response, err := client.Do(request)
	if err != nil {
		return -1, err
	}
	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 400 {
		return -1, fmt.Errorf("download http status %d", response.StatusCode)
	}

	count, copyError := io.CopyN(io.Discard, response.Body, speedTestDownloadBytes)
	elapsed := time.Since(start)

	if count <= 0 {
		if copyError == nil {
			copyError = io.ErrUnexpectedEOF
		}

		return -1, copyError
	}

	return bytesPerSecond(count, elapsed), nil
}

func testUpload(ctx context.Context, proxy C.Proxy) (int64, error) {
	client, cleanup, err := createProxyClient(ctx, proxy, speedTestUploadURL)
	if err != nil {
		return -1, err
	}
	defer cleanup()

	body := &countingReader{r: io.LimitReader(zeroReader{}, speedTestUploadBytes)}

	request, err := http.NewRequestWithContext(ctx, http.MethodPost, speedTestUploadURL, body)
	if err != nil {
		return -1, err
	}

	request.ContentLength = speedTestUploadBytes
	request.Header.Set("Content-Type", "application/octet-stream")

	start := time.Now()
	response, err := client.Do(request)
	elapsed := time.Since(start)

	if err != nil {
		if body.count > 0 {
			return bytesPerSecond(body.count, elapsed), nil
		}

		return -1, err
	}
	defer response.Body.Close()

	if response.StatusCode < 200 || response.StatusCode >= 400 {
		return -1, fmt.Errorf("upload http status %d", response.StatusCode)
	}

	if body.count <= 0 {
		return -1, io.ErrUnexpectedEOF
	}

	return bytesPerSecond(body.count, elapsed), nil
}

func createProxyClient(ctx context.Context, proxy C.Proxy, rawURL string) (*http.Client, func(), error) {
	target, err := url.Parse(rawURL)
	if err != nil {
		return nil, nil, err
	}

	host := target.Hostname()
	port := target.Port()

	if port == "" {
		switch target.Scheme {
		case "https":
			port = "443"
		case "http":
			port = "80"
		default:
			return nil, nil, fmt.Errorf("unsupported scheme %s", target.Scheme)
		}
	}

	metadata := &C.Metadata{
		NetWork:  C.TCP,
		AddrType: C.AtypDomainName,
		Host:     host,
		DstPort:  port,
	}

	if ip := net.ParseIP(host); ip != nil {
		metadata.Host = ""
		metadata.DstIP = ip
		if ip.To4() != nil {
			metadata.AddrType = C.AtypIPv4
		} else {
			metadata.AddrType = C.AtypIPv6
		}
	}

	connection, err := proxy.DialContext(ctx, metadata)
	if err != nil {
		return nil, nil, err
	}

	used := false
	transport := &http.Transport{
		Dial: func(network, address string) (net.Conn, error) {
			if used {
				return nil, fmt.Errorf("speed test connection already used")
			}
			used = true

			return connection, nil
		},
		TLSClientConfig:     &tls.Config{ServerName: host},
		TLSHandshakeTimeout: 5 * time.Second,
		DisableKeepAlives:   true,
		ForceAttemptHTTP2:   false,
	}

	client := &http.Client{
		Transport: transport,
		CheckRedirect: func(request *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
	}

	cleanup := func() {
		_ = connection.Close()
		transport.CloseIdleConnections()
	}

	return client, cleanup, nil
}

func bytesPerSecond(count int64, elapsed time.Duration) int64 {
	if count <= 0 {
		return -1
	}
	if elapsed <= 0 {
		elapsed = time.Millisecond
	}

	return int64(float64(count) / elapsed.Seconds())
}

type zeroReader struct{}

func (zeroReader) Read(buffer []byte) (int, error) {
	for index := range buffer {
		buffer[index] = 0
	}

	return len(buffer), nil
}

type countingReader struct {
	r     io.Reader
	count int64
}

func (reader *countingReader) Read(buffer []byte) (int, error) {
	count, err := reader.r.Read(buffer)
	reader.count += int64(count)

	return count, err
}

func AutoSelectProxy(name string) string {
	if name == "" {
		return ""
	}

	if tunnel.Mode() == tunnel.Global && selectProxyInGroup("GLOBAL", name) {
		return "GLOBAL"
	}

	for _, groupName := range QueryProxyGroupNames(false) {
		if groupName == "GLOBAL" {
			continue
		}

		if selectProxyInGroup(groupName, name) {
			return groupName
		}
	}

	if tunnel.Mode() != tunnel.Global && selectProxyInGroup("GLOBAL", name) {
		return "GLOBAL"
	}

	return ""
}

func selectProxyInGroup(groupName, proxyName string) bool {
	value := tunnel.Proxies()[groupName]
	if value == nil {
		return false
	}

	wrapped, ok := value.(*adapter.Proxy)
	if !ok {
		return false
	}

	group, ok := wrapped.ProxyAdapter.(outboundgroup.ProxyGroup)
	if !ok {
		return false
	}

	selector, ok := group.(*outboundgroup.Selector)
	if !ok {
		return false
	}

	if err := selector.Set(proxyName); err != nil {
		return false
	}

	closeConnByGroup(groupName)

	return true
}
