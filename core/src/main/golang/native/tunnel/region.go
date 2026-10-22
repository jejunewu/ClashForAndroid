package tunnel

import (
	"strings"
	"unicode"
)

var regionByCode = map[string]string{
	"AD": "安道尔", "AE": "阿联酋", "AF": "阿富汗", "AL": "阿尔巴尼亚", "AM": "亚美尼亚",
	"AO": "安哥拉", "AR": "阿根廷", "AT": "奥地利", "AU": "澳大利亚", "AZ": "阿塞拜疆",
	"BA": "波黑", "BD": "孟加拉", "BE": "比利时", "BF": "布基纳法索", "BG": "保加利亚",
	"BH": "巴林", "BI": "布隆迪", "BJ": "贝宁", "BN": "文莱", "BO": "玻利维亚",
	"BR": "巴西", "BT": "不丹", "BW": "博茨瓦纳", "BY": "白俄罗斯", "CA": "加拿大",
	"CD": "刚果民主共和国", "CF": "中非", "CG": "刚果", "CH": "瑞士", "CI": "科特迪瓦",
	"CL": "智利", "CM": "喀麦隆", "CN": "中国", "CO": "哥伦比亚", "CR": "哥斯达黎加",
	"CU": "古巴", "CV": "佛得角", "CY": "塞浦路斯", "CZ": "捷克", "DE": "德国",
	"DJ": "吉布提", "DK": "丹麦", "DO": "多米尼加", "DZ": "阿尔及利亚", "EC": "厄瓜多尔",
	"EE": "爱沙尼亚", "EG": "埃及", "ER": "厄立特里亚", "ES": "西班牙", "ET": "埃塞俄比亚",
	"FI": "芬兰", "FJ": "斐济", "FO": "法罗群岛", "FR": "法国", "GA": "加蓬",
	"GB": "英国", "GE": "格鲁吉亚", "GH": "加纳", "GI": "直布罗陀", "GL": "格陵兰",
	"GM": "冈比亚", "GN": "几内亚", "GQ": "赤道几内亚", "GR": "希腊", "GT": "危地马拉",
	"GU": "关岛", "HK": "香港", "HN": "洪都拉斯", "HR": "克罗地亚", "HT": "海地",
	"HU": "匈牙利", "ID": "印尼", "IE": "爱尔兰", "IL": "以色列", "IM": "马恩岛",
	"IN": "印度", "IQ": "伊拉克", "IR": "伊朗", "IS": "冰岛", "IT": "意大利",
	"JM": "牙买加", "JO": "约旦", "JP": "日本", "KE": "肯尼亚", "KG": "吉尔吉斯斯坦",
	"KH": "柬埔寨", "KR": "韩国", "KW": "科威特", "KY": "开曼群岛", "KZ": "哈萨克斯坦",
	"LA": "老挝", "LB": "黎巴嫩", "LK": "斯里兰卡", "LR": "利比里亚", "LS": "莱索托",
	"LT": "立陶宛", "LU": "卢森堡", "LV": "拉脱维亚", "LY": "利比亚", "MA": "摩洛哥",
	"MD": "摩尔多瓦", "ME": "黑山", "MG": "马达加斯加", "MK": "北马其顿", "ML": "马里",
	"MM": "缅甸", "MN": "蒙古", "MO": "澳门", "MR": "毛里塔尼亚", "MT": "马耳他",
	"MU": "毛里求斯", "MV": "马尔代夫", "MW": "马拉维", "MX": "墨西哥", "MY": "马来西亚",
	"MZ": "莫桑比克", "NA": "纳米比亚", "NE": "尼日尔", "NG": "尼日利亚", "NI": "尼加拉瓜",
	"NL": "荷兰", "NO": "挪威", "NP": "尼泊尔", "NZ": "新西兰", "OM": "阿曼",
	"PA": "巴拿马", "PE": "秘鲁", "PG": "巴布亚新几内亚", "PH": "菲律宾", "PK": "巴基斯坦",
	"PL": "波兰", "PR": "波多黎各", "PT": "葡萄牙", "PY": "巴拉圭", "QA": "卡塔尔",
	"RE": "留尼汪", "RO": "罗马尼亚", "RS": "塞尔维亚", "RU": "俄罗斯", "RW": "卢旺达",
	"SA": "沙特", "SC": "塞舌尔", "SD": "苏丹", "SE": "瑞典", "SG": "新加坡",
	"SI": "斯洛文尼亚", "SK": "斯洛伐克", "SL": "塞拉利昂", "SN": "塞内加尔", "SO": "索马里",
	"SS": "南苏丹", "ST": "圣多美和普林西比", "SV": "萨尔瓦多", "SZ": "斯威士兰", "TD": "乍得",
	"TG": "多哥", "TH": "泰国", "TJ": "塔吉克斯坦", "TL": "东帝汶", "TM": "土库曼斯坦",
	"TN": "突尼斯", "TR": "土耳其", "TT": "特立尼达和多巴哥", "TW": "台湾", "TZ": "坦桑尼亚",
	"UA": "乌克兰", "UG": "乌干达", "UK": "英国", "US": "美国", "UY": "乌拉圭",
	"UZ": "乌兹别克斯坦", "VE": "委内瑞拉", "VG": "英属维尔京群岛", "VI": "美属维尔京群岛", "VN": "越南",
	"ZA": "南非", "ZM": "赞比亚", "ZW": "津巴布韦",
}

type regionAlias struct {
	alias  string
	region string
}

var chineseRegionAliases = []regionAlias{
	{"美国", "美国"}, {"美國", "美国"}, {"香港", "香港"}, {"香港特别行政区", "香港"},
	{"台湾", "台湾"}, {"台灣", "台湾"}, {"台湾地区", "台湾"}, {"台北", "台湾"},
	{"日本", "日本"}, {"东京", "日本"}, {"大阪", "日本"}, {"新加坡", "新加坡"}, {"狮城", "新加坡"},
	{"韩国", "韩国"}, {"韓國", "韩国"}, {"首尔", "韩国"}, {"英国", "英国"}, {"英國", "英国"},
	{"德国", "德国"}, {"德國", "德国"}, {"法国", "法国"}, {"法國", "法国"}, {"荷兰", "荷兰"},
	{"荷蘭", "荷兰"}, {"加拿大", "加拿大"}, {"澳大利亚", "澳大利亚"}, {"澳洲", "澳大利亚"},
	{"新西兰", "新西兰"}, {"紐西蘭", "新西兰"}, {"俄罗斯", "俄罗斯"}, {"俄羅斯", "俄罗斯"},
	{"印度", "印度"}, {"土耳其", "土耳其"}, {"马来西亚", "马来西亚"}, {"馬來西亞", "马来西亚"},
	{"泰国", "泰国"}, {"泰國", "泰国"}, {"越南", "越南"}, {"菲律宾", "菲律宾"}, {"菲律賓", "菲律宾"},
	{"印尼", "印尼"}, {"印度尼西亚", "印尼"}, {"印度尼西亞", "印尼"}, {"阿联酋", "阿联酋"},
	{"阿聯酋", "阿联酋"}, {"巴西", "巴西"}, {"阿根廷", "阿根廷"}, {"智利", "智利"},
	{"墨西哥", "墨西哥"}, {"南非", "南非"}, {"瑞士", "瑞士"}, {"瑞典", "瑞典"},
	{"西班牙", "西班牙"}, {"意大利", "意大利"}, {"波兰", "波兰"}, {"波蘭", "波兰"},
	{"爱尔兰", "爱尔兰"}, {"愛爾蘭", "爱尔兰"}, {"芬兰", "芬兰"}, {"芬蘭", "芬兰"},
	{"挪威", "挪威"}, {"丹麦", "丹麦"}, {"丹麥", "丹麦"}, {"奥地利", "奥地利"},
	{"奧地利", "奥地利"}, {"比利时", "比利时"}, {"比利時", "比利时"}, {"捷克", "捷克"},
	{"罗马尼亚", "罗马尼亚"}, {"羅馬尼亞", "罗马尼亚"}, {"乌克兰", "乌克兰"}, {"烏克蘭", "乌克兰"},
	{"以色列", "以色列"}, {"沙特", "沙特"}, {"沙特阿拉伯", "沙特"}, {"卡塔尔", "卡塔尔"},
	{"澳门", "澳门"}, {"澳門", "澳门"}, {"中国", "中国"}, {"中國", "中国"},
	{"洛杉矶", "美国"}, {"硅谷", "美国"}, {"圣何塞", "美国"}, {"纽约", "美国"}, {"西雅图", "美国"},
	{"达拉斯", "美国"}, {"芝加哥", "美国"}, {"迈阿密", "美国"}, {"波士顿", "美国"}, {"华盛顿", "美国"},
	{"新北", "台湾"}, {"台中", "台湾"}, {"高雄", "台湾"}, {"名古屋", "日本"}, {"福冈", "日本"},
	{"釜山", "韩国"}, {"仁川", "韩国"}, {"伦敦", "英国"}, {"曼彻斯特", "英国"},
	{"法兰克福", "德国"}, {"柏林", "德国"}, {"慕尼黑", "德国"}, {"巴黎", "法国"},
	{"阿姆斯特丹", "荷兰"}, {"鹿特丹", "荷兰"}, {"多伦多", "加拿大"}, {"温哥华", "加拿大"},
	{"蒙特利尔", "加拿大"}, {"悉尼", "澳大利亚"}, {"墨尔本", "澳大利亚"}, {"布里斯班", "澳大利亚"},
	{"莫斯科", "俄罗斯"}, {"圣彼得堡", "俄罗斯"}, {"孟买", "印度"}, {"新德里", "印度"},
	{"伊斯坦布尔", "土耳其"}, {"吉隆坡", "马来西亚"}, {"曼谷", "泰国"}, {"胡志明", "越南"},
	{"河内", "越南"}, {"马尼拉", "菲律宾"}, {"雅加达", "印尼"}, {"迪拜", "阿联酋"},
	{"阿布扎比", "阿联酋"}, {"圣保罗", "巴西"}, {"布宜诺斯艾利斯", "阿根廷"}, {"墨西哥城", "墨西哥"},
	{"约翰内斯堡", "南非"}, {"开普敦", "南非"}, {"苏黎世", "瑞士"}, {"斯德哥尔摩", "瑞典"},
	{"马德里", "西班牙"}, {"巴塞罗那", "西班牙"}, {"米兰", "意大利"}, {"罗马", "意大利"},
	{"华沙", "波兰"}, {"都柏林", "爱尔兰"}, {"赫尔辛基", "芬兰"}, {"奥斯陆", "挪威"},
	{"哥本哈根", "丹麦"}, {"维也纳", "奥地利"}, {"布鲁塞尔", "比利时"}, {"布拉格", "捷克"},
	{"布加勒斯特", "罗马尼亚"}, {"基辅", "乌克兰"}, {"特拉维夫", "以色列"}, {"利雅得", "沙特"},
	{"多哈", "卡塔尔"},
}

var englishRegionAliases = []regionAlias{
	{"united states", "美国"}, {"united states of america", "美国"}, {"america", "美国"}, {"usa", "美国"},
	{"hong kong", "香港"}, {"hongkong", "香港"}, {"taiwan", "台湾"}, {"japan", "日本"},
	{"singapore", "新加坡"}, {"south korea", "韩国"}, {"korea", "韩国"}, {"united kingdom", "英国"},
	{"england", "英国"}, {"germany", "德国"}, {"france", "法国"}, {"netherlands", "荷兰"},
	{"canada", "加拿大"}, {"australia", "澳大利亚"}, {"new zealand", "新西兰"}, {"russia", "俄罗斯"},
	{"india", "印度"}, {"turkey", "土耳其"}, {"malaysia", "马来西亚"}, {"thailand", "泰国"},
	{"vietnam", "越南"}, {"philippines", "菲律宾"}, {"indonesia", "印尼"}, {"united arab emirates", "阿联酋"},
	{"uae", "阿联酋"}, {"brazil", "巴西"}, {"argentina", "阿根廷"}, {"chile", "智利"},
	{"mexico", "墨西哥"}, {"south africa", "南非"}, {"switzerland", "瑞士"}, {"sweden", "瑞典"},
	{"spain", "西班牙"}, {"italy", "意大利"}, {"poland", "波兰"}, {"ireland", "爱尔兰"},
	{"finland", "芬兰"}, {"norway", "挪威"}, {"denmark", "丹麦"}, {"austria", "奥地利"},
	{"belgium", "比利时"}, {"czech", "捷克"}, {"romania", "罗马尼亚"}, {"ukraine", "乌克兰"},
	{"israel", "以色列"}, {"saudi arabia", "沙特"}, {"qatar", "卡塔尔"}, {"macau", "澳门"},
	{"macao", "澳门"}, {"china", "中国"},
	{"los angeles", "美国"}, {"san jose", "美国"}, {"silicon valley", "美国"}, {"new york", "美国"},
	{"seattle", "美国"}, {"dallas", "美国"}, {"chicago", "美国"}, {"miami", "美国"},
	{"boston", "美国"}, {"washington", "美国"}, {"taipei", "台湾"}, {"taichung", "台湾"},
	{"kaohsiung", "台湾"}, {"tokyo", "日本"}, {"osaka", "日本"}, {"nagoya", "日本"},
	{"seoul", "韩国"}, {"busan", "韩国"}, {"london", "英国"}, {"manchester", "英国"},
	{"frankfurt", "德国"}, {"berlin", "德国"}, {"munich", "德国"}, {"paris", "法国"},
	{"amsterdam", "荷兰"}, {"rotterdam", "荷兰"}, {"toronto", "加拿大"}, {"vancouver", "加拿大"},
	{"montreal", "加拿大"}, {"sydney", "澳大利亚"}, {"melbourne", "澳大利亚"}, {"brisbane", "澳大利亚"},
	{"moscow", "俄罗斯"}, {"saint petersburg", "俄罗斯"}, {"mumbai", "印度"}, {"new delhi", "印度"},
	{"istanbul", "土耳其"}, {"kuala lumpur", "马来西亚"}, {"bangkok", "泰国"}, {"ho chi minh", "越南"},
	{"hanoi", "越南"}, {"manila", "菲律宾"}, {"jakarta", "印尼"}, {"dubai", "阿联酋"},
	{"abu dhabi", "阿联酋"}, {"sao paulo", "巴西"}, {"buenos aires", "阿根廷"}, {"mexico city", "墨西哥"},
	{"johannesburg", "南非"}, {"cape town", "南非"}, {"zurich", "瑞士"}, {"stockholm", "瑞典"},
	{"madrid", "西班牙"}, {"barcelona", "西班牙"}, {"milan", "意大利"}, {"rome", "意大利"},
	{"warsaw", "波兰"}, {"dublin", "爱尔兰"}, {"helsinki", "芬兰"}, {"oslo", "挪威"},
	{"copenhagen", "丹麦"}, {"vienna", "奥地利"}, {"brussels", "比利时"}, {"prague", "捷克"},
	{"bucharest", "罗马尼亚"}, {"kyiv", "乌克兰"}, {"kiev", "乌克兰"}, {"tel aviv", "以色列"},
	{"riyadh", "沙特"}, {"doha", "卡塔尔"},
}

var englishCodeAliases = map[string]string{
	"us": "美国", "usa": "美国", "hk": "香港", "tw": "台湾", "jp": "日本", "sg": "新加坡",
	"kr": "韩国", "uk": "英国", "gb": "英国", "de": "德国", "fr": "法国", "nl": "荷兰",
	"ca": "加拿大", "au": "澳大利亚", "nz": "新西兰", "ru": "俄罗斯", "in": "印度",
	"tr": "土耳其", "my": "马来西亚", "th": "泰国", "vn": "越南", "ph": "菲律宾",
	"id": "印尼", "ae": "阿联酋", "br": "巴西", "ar": "阿根廷", "cl": "智利",
	"mx": "墨西哥", "za": "南非", "ch": "瑞士", "se": "瑞典", "es": "西班牙",
	"it": "意大利", "pl": "波兰", "ie": "爱尔兰", "fi": "芬兰", "no": "挪威",
	"dk": "丹麦", "at": "奥地利", "be": "比利时", "cz": "捷克", "ro": "罗马尼亚",
	"ua": "乌克兰", "il": "以色列", "sa": "沙特", "qa": "卡塔尔", "mo": "澳门", "cn": "中国",
}

func detectRegion(name string) string {
	name = strings.TrimSpace(name)
	if name == "" {
		return "其他"
	}

	if region := regionFromFlag(name); region != "" {
		return region
	}

	for _, item := range chineseRegionAliases {
		if strings.Contains(name, item.alias) {
			return item.region
		}
	}

	lower := strings.ToLower(name)
	for _, item := range englishRegionAliases {
		if strings.Contains(lower, item.alias) {
			return item.region
		}
	}

	for _, token := range strings.FieldsFunc(lower, func(r rune) bool {
		return !unicode.IsLetter(r) && !unicode.IsDigit(r)
	}) {
		if region, ok := englishCodeAliases[token]; ok {
			return region
		}

		if len(token) > 2 && len(token) <= 5 {
			prefix := token[:2]
			if region, ok := englishCodeAliases[prefix]; ok {
				rest := token[2:]
				allDigits := true
				for _, r := range rest {
					if !unicode.IsDigit(r) {
						allDigits = false
						break
					}
				}
				if allDigits {
					return region
				}
			}
		}
	}

	return "其他"
}

func regionFromFlag(name string) string {
	runes := []rune(name)
	for i := 0; i+1 < len(runes); i++ {
		first := runes[i]
		second := runes[i+1]
		if isRegionalIndicator(first) && isRegionalIndicator(second) {
			code := string([]rune{
				rune(first-0x1F1E6) + 'A',
				rune(second-0x1F1E6) + 'A',
			})
			if region, ok := regionByCode[code]; ok {
				return region
			}
			return code
		}
	}

	return ""
}

func isRegionalIndicator(r rune) bool {
	return r >= 0x1F1E6 && r <= 0x1F1FF
}
