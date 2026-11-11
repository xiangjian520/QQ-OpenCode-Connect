package com.xiangjian.qqbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangjian.qqbot.domain.Conversation;
import com.xiangjian.qqbot.domain.InboundMessage;
import com.xiangjian.qqbot.domain.MessageRow;
import com.xiangjian.qqbot.repository.EntertainmentGameRepository;
import com.xiangjian.qqbot.repository.FunSocialRepository;
import com.xiangjian.qqbot.repository.MessageRepository;
import com.xiangjian.qqbot.service.entertainment.FunApiClient;
import com.xiangjian.qqbot.service.entertainment.GuessNumberEngine;
import com.xiangjian.qqbot.service.entertainment.TicTacToeEngine;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EntertainmentService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter REMINDER_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZONE);
    private static final Pattern DURATION = Pattern.compile("^(\\d{1,4})(秒|分钟|分|小时|时|天)$");
    private static final Set<String> COMMANDS = Set.of(
            "娱乐", "运势", "塔罗", "抽签", "小猪", "今日小猪", "随机小猪", "今日老婆", "星座",
            "笑话", "冷知识", "名言", "梗", "夸夸", "安慰", "选择", "转盘", "硬币", "骰子",
            "井字棋", "猜数字", "表情", "表情包", "合成", "天气", "番剧", "新番", "B站热门",
            "Steam", "点歌", "词云", "签到", "我的积分", "积分榜", "提醒", "投票"
    );

    private static final List<PigAsset> PIGS = List.of(
            new PigAsset("元气小猪", "/entertainment/pigs/pig.png"),
            new PigAsset("彩虹小猪", "/entertainment/pigs/rainbow-pig.png"),
            new PigAsset("雪地小猪", "/entertainment/pigs/snow-pig.png"),
            new PigAsset("苹果小猪", "/entertainment/pigs/apple-pig.png"),
            new PigAsset("魔法小猪", "/entertainment/pigs/magic-pig.png"),
            new PigAsset("猫猫小猪", "/entertainment/pigs/pig-cat.png"),
            new PigAsset("面包小猪", "/entertainment/pigs/pig-bun.png"),
            new PigAsset("坦克小猪", "/entertainment/pigs/tank-pig.png"),
            new PigAsset("出租车小猪", "/entertainment/pigs/taxi-pig.png"),
            new PigAsset("西瓜小猪", "/entertainment/pigs/watermelon-pig.png"),
            new PigAsset("油画小猪", "/entertainment/pigs/vangogh-pig.png"),
            new PigAsset("云朵小猪", "/entertainment/pigs/cloud-pig.jpg"),
            new PigAsset("诸葛小猪", "/entertainment/pigs/zhuge-pig.png"),
            new PigAsset("普通小猪", "/entertainment/pigs/just-pig.jpg")
    );
    private static final List<ReactionAsset> REACTIONS = List.of(
            new ReactionAsset("wink", "眨眼", "/entertainment/mfaces/wink.gif", List.of("眨眼", "wink")),
            new ReactionAsset("down", "下头", "/entertainment/mfaces/down.gif", List.of("下头", "无语住")),
            new ReactionAsset("confused", "何意味", "/entertainment/mfaces/confused.gif", List.of("疑惑", "何意味", "问号")),
            new ReactionAsset("peek", "偷看", "/entertainment/mfaces/peek.gif", List.of("偷看", "暗中观察")),
            new ReactionAsset("cute", "卖萌", "/entertainment/mfaces/cute.gif", List.of("卖萌", "可爱", "撒娇")),
            new ReactionAsset("pleading", "可怜巴巴", "/entertainment/mfaces/pleading.gif", List.of("可怜", "拜托", "求求")),
            new ReactionAsset("sigh", "叹气", "/entertainment/mfaces/sigh.gif", List.of("叹气", "无奈")),
            new ReactionAsset("melon", "吃瓜", "/entertainment/mfaces/melon.gif", List.of("吃瓜", "围观", "看戏")),
            new ReactionAsset("bite", "咬", "/entertainment/mfaces/bite.gif", List.of("咬", "生气")),
            new ReactionAsset("hiss", "哈气", "/entertainment/mfaces/hiss.gif", List.of("哈气", "凶")),
            new ReactionAsset("hmph", "哼", "/entertainment/mfaces/hmph.gif", List.of("哼", "傲娇")),
            new ReactionAsset("love", "喜欢", "/entertainment/mfaces/love.gif", List.of("喜欢", "爱心", "贴贴")),
            new ReactionAsset("stop", "坏猫止步", "/entertainment/mfaces/stop.gif", List.of("停止", "坏猫", "不可以")),
            new ReactionAsset("shy", "害羞", "/entertainment/mfaces/shy.gif", List.of("害羞", "脸红")),
            new ReactionAsset("happy", "开心", "/entertainment/mfaces/happy.gif", List.of("开心", "高兴", "好耶")),
            new ReactionAsset("think", "思考", "/entertainment/mfaces/think.gif", List.of("思考", "想想")),
            new ReactionAsset("speechless", "无语", "/entertainment/mfaces/speechless.gif", List.of("无语", "沉默")),
            new ReactionAsset("faint", "晕厥", "/entertainment/mfaces/faint.gif", List.of("晕", "晕厥", "倒地")),
            new ReactionAsset("furious", "火猫三丈", "/entertainment/mfaces/furious.gif", List.of("愤怒", "生气", "火大")),
            new ReactionAsset("laugh", "笑", "/entertainment/mfaces/laugh.gif", List.of("笑", "哈哈", "乐")),
            new ReactionAsset("nervous", "紧张", "/entertainment/mfaces/nervous.gif", List.of("紧张", "慌")),
            new ReactionAsset("rest", "趴", "/entertainment/mfaces/rest.gif", List.of("趴", "躺", "休息")),
            new ReactionAsset("dark", "黑化", "/entertainment/mfaces/dark.gif", List.of("黑化", "坏掉")),
            new ReactionAsset("furious-cat", "火猫", "/entertainment/mfaces/furious.gif", List.of("火猫"))
    );
    private static final List<TarotCard> TAROT = List.of(
            new TarotCard("愚者", "新的开始、好奇心与自由", "鲁莽、准备不足与逃避", "/entertainment/tarot/fool.jpg"),
            new TarotCard("魔术师", "行动力、创造力与资源整合", "空想、分心与能力浪费", "/entertainment/tarot/magician.jpg"),
            new TarotCard("女祭司", "直觉、安静观察与内在知识", "忽视直觉、信息不足与封闭", "/entertainment/tarot/high-priestess.jpg"),
            new TarotCard("皇后", "温柔、丰盛与创造力", "过度付出、依赖与停滞", "/entertainment/tarot/empress.jpg"),
            new TarotCard("皇帝", "秩序、责任与稳定", "固执、控制欲与僵硬", "/entertainment/tarot/emperor.jpg"),
            new TarotCard("教皇", "学习、传统与可靠建议", "教条、盲从与缺少独立思考", "/entertainment/tarot/hierophant.jpg"),
            new TarotCard("恋人", "选择、默契与真诚连接", "犹豫、价值冲突与逃避选择", "/entertainment/tarot/lovers.jpg"),
            new TarotCard("战车", "意志、推进与胜利", "失控、急躁与方向不明", "/entertainment/tarot/chariot.jpg"),
            new TarotCard("力量", "耐心、勇气与温柔的坚持", "自我怀疑、消耗与退缩", "/entertainment/tarot/strength.jpg"),
            new TarotCard("隐者", "独处、复盘与寻找答案", "孤立、停滞与拒绝沟通", "/entertainment/tarot/hermit.jpg"),
            new TarotCard("命运之轮", "转机、周期与顺势而为", "重复旧模式、时机未到", "/entertainment/tarot/wheel-of-fortune.jpg"),
            new TarotCard("正义", "平衡、诚实与清晰边界", "偏颇、逃避责任与失衡", "/entertainment/tarot/justice.jpg"),
            new TarotCard("倒吊人", "暂停、换位思考与新视角", "无谓牺牲、拖延与卡住", "/entertainment/tarot/hanged-man.jpg"),
            new TarotCard("死神", "结束旧阶段、清理与转化", "抗拒变化、拖泥带水", "/entertainment/tarot/death.jpg"),
            new TarotCard("节制", "协调、节奏与自我照顾", "过载、失衡与急于求成", "/entertainment/tarot/temperance.jpg"),
            new TarotCard("恶魔", "欲望、惯性提醒与边界课题", "挣脱束缚、看清诱惑", "/entertainment/tarot/devil.jpg"),
            new TarotCard("高塔", "打破旧结构、突然的真相", "对变化的恐惧、延迟崩解", "/entertainment/tarot/tower.jpg"),
            new TarotCard("星星", "希望、疗愈与长期愿景", "失望、信心不足与迷茫", "/entertainment/tarot/star.jpg"),
            new TarotCard("月亮", "潜意识、想象与不确定", "迷雾散开、真相浮现", "/entertainment/tarot/moon.jpg"),
            new TarotCard("太阳", "活力、成功与坦然", "短暂低落、过度乐观", "/entertainment/tarot/sun.jpg"),
            new TarotCard("审判", "觉醒、复盘与重新决定", "自责、逃避总结与迟疑", "/entertainment/tarot/judgement.jpg"),
            new TarotCard("世界", "完成、整合与阶段性收获", "收尾未完成、缺少闭环", "/entertainment/tarot/world.jpg")
    );
    private static final List<String> LUCKY_COLORS = List.of(
            "樱花粉", "奶油白", "星夜紫", "薄荷绿", "夕阳橙", "天空蓝", "月光银"
    );
    private static final List<String> GOOD_THINGS = List.of(
            "主动和喜欢的人说一句话", "整理桌面", "喝一杯热饮", "听一首老歌", "早睡半小时",
            "完成一件拖延的小事", "给朋友发一句问候", "出门散步", "读几页书", "尝试新事物"
    );
    private static final List<String> BAD_THINGS = List.of(
            "睡前刷太久手机", "空腹喝冰饮", "把情绪憋在心里", "冲动消费", "熬夜赶工",
            "忘记保存文件", "和键盘较劲", "一次答应太多事情", "错过饭点", "过度自我否定"
    );
    private static final List<String> JOKES = List.of(
            "为什么程序员总分不清万圣节和圣诞节？因为 Oct 31 等于 Dec 25。",
            "我跟电脑说：你要学会独立思考。它回答：请先连接网络。",
            "猫为什么不喜欢上学？因为老师总让它做喵练习题。",
            "朋友问我为什么不运动。我说：我在练习静止蓄力。",
            "今天准备早睡，结果研究了三个小时怎样早睡。",
            "我的钱包很安静，因为它已经很久没说话了。",
            "老板说公司像一家人。我立刻问：那压岁钱什么时候发？",
            "为什么咖啡师很可靠？因为他从不让你空杯。",
            "减肥第一天：把零食放进柜子，晚上又把它请了出来。",
            "闹钟最大的错觉：再睡五分钟就会起床。",
            "我做了一个很勇敢的决定：把待办事项改成明天。",
            "Wi-Fi 和猫有什么共同点？断一下都会让人焦虑。"
    );
    private static final List<String> FACTS = List.of(
            "蜂蜜在合适保存条件下可以存放很久，考古发现过仍可食用的古代蜂蜜。",
            "章鱼有三个心脏，血液中含有血蓝蛋白，因此呈蓝色。",
            "香蕉在植物学上属于浆果，而草莓并不属于真正的浆果。",
            "地球上约 96.5% 的水在海洋中，可直接利用的淡水比例非常小。",
            "蜂鸟可以向后飞，是少数能够悬停的鸟类之一。",
            "人的胃黏膜会持续更新，以保护胃壁免受强酸影响。",
            "月球每年都在缓慢远离地球，速度约为几厘米。",
            "树木通过真菌网络与邻近植物交换水分和养分。",
            "猫的呼噜声频率可能有助于放松，但它并不只会在开心时呼噜。",
            "太阳光到达地球约需 8 分 20 秒，我们看到的太阳始终是过去的太阳。",
            "海獭睡觉时会互相牵手，避免被水流冲散。",
            "一些植物在受到触碰后会快速闭合叶片，例如含羞草。"
    );
    private static final List<Quote> QUOTES = List.of(
            new Quote("生活的理想，就是为了理想的生活。", "张闻天"),
            new Quote("不要等待机会，而要创造机会。", "佚名"),
            new Quote("知足且上进，温柔而坚定。", "佚名"),
            new Quote("路虽远，行则将至；事虽难，做则必成。", "古语"),
            new Quote("真正的自由，是有能力选择自己的节奏。", "佚名"),
            new Quote("把今天过好，就是对未来最可靠的准备。", "佚名"),
            new Quote("世界以痛吻我，要我报之以歌。", "泰戈尔"),
            new Quote("纵有疾风起，人生不言弃。", "保罗·瓦雷里"),
            new Quote("我们终其一生，都在寻找与自己相处的方式。", "佚名"),
            new Quote("慢一点没关系，方向正确就好。", "佚名"),
            new Quote("不要因为走得太远，忘记为什么出发。", "纪伯伦"),
            new Quote("愿你眼里有光，心中有爱，脚下有路。", "佚名")
    );
    private static final List<String> MEMES = List.of(
            "人在工位，心在漫展。",
            "这不是摆烂，这是战略性续航。",
            "已读不回，是当代赛博隐身术。",
            "今日份精神状态：稳定地不稳定。",
            "别催，灵感正在加载 99%。",
            "我不是拖延，我是在等待最佳时机。",
            "只要我不打开账单，消费就不存在。",
            "计划很丰满，执行力在做拉伸。",
            "小小困难，拿捏……等等，拿捏不住。",
            "早睡是一种传说，见过的人都说好。"
    );
    private static final List<String> PRAISES = List.of(
            "你的认真藏不住，连细节都在替你说“靠谱”。",
            "你有一种让普通日子变好看的能力。",
            "你的想法很亮，像阴天里突然打开的一扇窗。",
            "和你聊天很舒服，因为你总能认真接住别人的话。",
            "你不是慢，你是在稳稳地走自己的节奏。",
            "你的温柔不是软弱，是一种很有力量的坚定。",
            "今天也辛苦啦，能把生活一点点打理好已经很厉害。",
            "你身上有种不声张的闪光点，越相处越会发现。"
    );
    private static final List<String> COMFORTS = List.of(
            "先不用急着变好，今天能好好呼吸、好好吃饭就已经很棒。",
            "难过不代表你脆弱，只代表你真的在乎。",
            "允许自己停一下。休息不是退步，是给心留一点空间。",
            "这件事很重，但你不需要一个人立刻搬动它。",
            "今天做不完也没关系，明天还可以继续。",
            "你已经比昨天的自己多走了一点，只是有时自己没看见。",
            "不用向所有人解释自己，先照顾好真实的感受。",
            "如果暂时没有答案，就先喝杯温水，慢慢来。"
    );
    private static final List<String> OMIKUJI = List.of(
            "大吉 · 适合主动表达，今天说出的想法容易被听见。",
            "中吉 · 稳中向好，先完成最重要的一件事。",
            "小吉 · 小确幸正在路上，留意身边的小惊喜。",
            "吉 · 适合整理计划，把混乱变得清楚。",
            "末吉 · 不用着急，今天慢一点反而更顺。",
            "平 · 普通的一天也值得认真生活。",
            "小凶 · 少熬夜、少冲动，给自己留一点缓冲。",
            "凶 · 今天不适合硬碰硬，先保护好自己的节奏。"
    );
    private static final List<Zodiac> ZODIACS = List.of(
            new Zodiac("白羊座", "行动力", "今天适合先做再优化，但别把速度当成唯一答案。"),
            new Zodiac("金牛座", "稳定感", "把注意力放回可控的小事，一顿好饭也能修复心情。"),
            new Zodiac("双子座", "好奇心", "信息很多，挑一条真正有用的线索深入下去。"),
            new Zodiac("巨蟹座", "感受力", "照顾别人之前，也记得给情绪留一个出口。"),
            new Zodiac("狮子座", "表达欲", "今天适合被看见，但真诚比声势更有力量。"),
            new Zodiac("处女座", "秩序感", "完善细节很好，别让完美主义拖住开始。"),
            new Zodiac("天秤座", "平衡感", "做选择时听听自己的偏好，不必让所有人满意。"),
            new Zodiac("天蝎座", "专注力", "适合处理一直回避的问题，直球往往更省力。"),
            new Zodiac("射手座", "探索欲", "给日常加一点新鲜感，微小改变就能带来动力。"),
            new Zodiac("摩羯座", "执行力", "目标不必很大，今天完成一个关键步骤就很好。"),
            new Zodiac("水瓶座", "创造力", "跳出惯常路径看看，答案可能比想象中更有趣。"),
            new Zodiac("双鱼座", "想象力", "灵感丰富的一天，记得把喜欢的事落到一个小行动上。")
    );
    private static final Set<String> WORD_STOPS = Set.of(
            "这个", "那个", "什么", "怎么", "可以", "就是", "不是", "还是", "但是", "然后",
            "因为", "所以", "如果", "真的", "一下", "一个", "我们", "你们", "他们", "自己",
            "今天", "感觉", "已经", "没有", "现在", "知道", "这样", "那样", "哈哈", "哈哈哈"
    );
    private static final List<String> WIFE_ARCHETYPES = List.of(
            "樱花巫女", "图书塔魔女", "星海领航员", "元气剑士", "甜点店长",
            "月下侦探", "机械师少女", "森林药师", "舞台偶像", "午后画家",
            "风之信使", "猫耳咖啡师"
    );
    private static final List<String> WIFE_TRAITS = List.of(
            "温柔但很有主见", "安静、细心，偶尔会吐槽", "行动派，喜欢把事情一次做好",
            "好奇心旺盛，愿意陪你尝试新东西", "很会照顾人，但也要自己的空间",
            "嘴上说随便，其实早就想好了", "元气满满，容易把快乐传染给别人",
            "慢热但可靠，熟悉后会分享很多小秘密"
    );

    private final SettingsService settings;
    private final PermissionService permissions;
    private final EntertainmentGameRepository games;
    private final FunSocialRepository social;
    private final FunApiClient funApi;
    private final MessageRepository messages;
    private final CommandService commands;
    private final ObjectMapper objectMapper;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public EntertainmentService(SettingsService settings,
                                PermissionService permissions,
                                EntertainmentGameRepository games,
                                FunSocialRepository social,
                                FunApiClient funApi,
                                MessageRepository messages,
                                CommandService commands,
                                ObjectMapper objectMapper) {
        this.settings = settings;
        this.permissions = permissions;
        this.games = games;
        this.social = social;
        this.funApi = funApi;
        this.messages = messages;
        this.commands = commands;
        this.objectMapper = objectMapper;
    }

    public boolean tryHandle(InboundMessage message, Conversation conversation) {
        Optional<CommandParser.Command> parsed = CommandParser.parse(message.text());
        if (parsed.isEmpty() || !COMMANDS.contains(parsed.get().name())) {
            return false;
        }
        if (!settings.entertainmentEnabled()) {
            commands.reply(message, "娱乐功能当前已关闭，请在网页端“运行设置”中开启。");
            return true;
        }
        CommandParser.Command command = parsed.get();
        switch (command.name()) {
            case "娱乐" -> menu(message);
            case "运势" -> fortune(message);
            case "塔罗" -> tarot(message);
            case "抽签" -> omikuji(message);
            case "小猪", "今日小猪", "随机小猪" -> pig(message);
            case "今日老婆" -> wife(message);
            case "星座" -> zodiac(message);
            case "笑话" -> randomText(message, "笑话", JOKES);
            case "冷知识" -> randomText(message, "冷知识", FACTS);
            case "名言" -> quote(message);
            case "梗" -> randomText(message, "赛博梗", MEMES);
            case "夸夸" -> praise(message, command.args());
            case "安慰" -> randomText(message, "安慰", COMFORTS);
            case "选择" -> choose(message, command.args(), false);
            case "转盘" -> choose(message, command.args(), true);
            case "硬币" -> coin(message);
            case "骰子" -> dice(message, command.args());
            case "井字棋" -> withLock(conversation, () -> ticTacToe(message, conversation, command.args()));
            case "猜数字" -> withLock(conversation, () -> guessNumber(message, conversation, command.args()));
            case "表情", "表情包" -> reaction(message, command.args());
            case "合成" -> emojiMix(message, command.args());
            case "天气" -> commands.reply(message, funApi.weather(command.args()));
            case "番剧" -> commands.reply(message, funApi.bangumiSearch(command.args()));
            case "新番" -> commands.reply(message, funApi.bangumiToday());
            case "B站热门" -> commands.reply(message, funApi.bilibiliPopular());
            case "Steam" -> commands.reply(message, funApi.steamSearch(command.args()));
            case "点歌" -> commands.reply(message, funApi.musicSearch(command.args()));
            case "词云" -> wordCloud(message, conversation);
            case "签到" -> signIn(message);
            case "我的积分" -> profile(message);
            case "积分榜" -> leaderboard(message);
            case "提醒" -> reminder(message, command.args());
            case "投票" -> poll(message, conversation, command.args());
            default -> { return false; }
        }
        return true;
    }

    public Map<String, Object> overview() {
        List<Map<String, Object>> features = List.of(
                feature("今日运势", "每天一份轻松的小运势", "/运势"),
                feature("塔罗牌", "抽取一张带独立牌面的塔罗牌", "/塔罗"),
                feature("赛博抽签", "抽一张轻松向御神签", "/抽签"),
                feature("今日小猪", "今天是什么小猪？附带安全小猪表情", "/小猪"),
                feature("今日老婆", "安全的虚拟二次元人设签", "/今日老婆"),
                feature("星座日签", "查看十二星座的今日提示", "/星座 狮子"),
                feature("笑话", "随机一条轻松笑话", "/笑话"),
                feature("冷知识", "随机一条无害冷知识", "/冷知识"),
                feature("名言", "随机一句短文与署名", "/名言"),
                feature("赛博梗", "随机一句网络感文案", "/梗"),
                feature("夸夸", "给群友或自己一句真诚夸赞", "/夸夸"),
                feature("安慰", "随机一句温柔安慰", "/安慰"),
                feature("选择困难", "从多个选项中做决定", "/选择 火锅|烧烤|回家"),
                feature("转盘", "从多个选项中随机抽取", "/转盘 A|B|C"),
                feature("抛硬币", "正面还是反面", "/硬币"),
                feature("数字骰子", "支持 2-1000 面", "/骰子 20"),
                feature("井字棋", "与 Bot 下一盘井字棋", "/井字棋"),
                feature("猜数字", "在 1-100 范围内猜答案", "/猜数字"),
                feature("反应表情", "从安全表情包图库中选择一张", "/表情 吃瓜"),
                feature("Emoji 合成", "合成两个 Emoji Kitchen 表情", "/合成 😀 🐱"),
                feature("天气", "查询城市天气", "/天气 上海"),
                feature("Bangumi 番剧", "搜索番剧资料与评分", "/番剧 孤独摇滚"),
                feature("今日新番", "查看今天的放送日历", "/新番"),
                feature("B 站热门", "查看当前热门视频", "/B站热门"),
                feature("Steam 搜索", "搜索 Steam 游戏", "/Steam 星露谷物语"),
                feature("点歌", "搜索歌曲并返回网易云链接", "/点歌 晴天"),
                feature("词云", "统计当前会话的高频词", "/词云"),
                feature("签到", "每日签到获得积分", "/签到"),
                feature("我的积分", "查看签到积分和连续天数", "/我的积分"),
                feature("积分榜", "查看当前积分排行", "/积分榜"),
                feature("提醒", "创建持久化提醒", "/提醒 10分钟 喝水"),
                feature("投票", "创建、参与和查看投票", "/投票 发起 午饭吃什么|面|饭")
        );
        List<Map<String, Object>> groups = List.of(
                group("日常娱乐", features.subList(0, 9)),
                group("随机与文字", features.subList(9, 18)),
                group("表情与媒体", features.subList(18, 26)),
                group("互动工具", features.subList(26, features.size()))
        );
        return Map.of(
                "enabled", settings.entertainmentEnabled(),
                "activeGames", games.countActive(),
                "recentGames", games.recent(20),
                "featureCount", features.size(),
                "features", features,
                "featureGroups", groups,
                "pigGallery", PIGS.stream().map(pig -> Map.of("name", pig.name(), "image", pig.image())).toList(),
                "tarotGallery", TAROT.stream().map(card -> Map.of("name", card.name(), "image", card.image())).toList(),
                "stickerGallery", REACTIONS.stream().limit(23).map(item ->
                        Map.of("name", item.name(), "image", item.image(), "keywords", item.aliases())).toList(),
                "pollActive", social.poll("web:WEB:admin").isPresent()
        );
    }

    private void menu(InboundMessage message) {
        commands.reply(message, """
                安全娱乐中心 · 共 32 项
                日常：/运势 /塔罗 /抽签 /小猪 /今日老婆 /星座 /笑话 /冷知识 /名言
                随机：/梗 /夸夸 /安慰 /选择 A|B /转盘 A|B /硬币 /骰子
                游戏：/井字棋 /猜数字
                表情：/表情 [关键词] /合成 😀 🐱
                ACG：/番剧 关键词 /新番 /B站热门 /Steam 关键词 /点歌 关键词
                社交：/词云 /签到 /我的积分 /积分榜 /提醒 10分钟 喝水 /投票
                投票用法：/投票 发起 问题|选项1|选项2，之后发送 /投票 编号。
                本模块不包含 NSFW、成人图源或色情搜索。""");
    }

    private void fortune(InboundMessage message) {
        Random random = dailyRandom(message.userId(), "fortune");
        int score = 35 + random.nextInt(66);
        String color = pick(LUCKY_COLORS, random);
        String good = pick(GOOD_THINGS, random);
        String bad = pick(BAD_THINGS, random);
        int luckyNumber = 1 + random.nextInt(99);
        commands.reply(message, """
                今日运势：%d/100
                幸运色：%s
                幸运数字：%d
                宜：%s
                忌：%s
                仅供娱乐，别让随机数替你做大决定喵。""".formatted(score, color, luckyNumber, good, bad));
    }

    private void tarot(InboundMessage message) {
        Random random = dailyRandom(message.userId(), "tarot-" + LocalDate.now(ZONE));
        TarotCard card = TAROT.get(random.nextInt(TAROT.size()));
        boolean reversed = random.nextBoolean();
        String meaning = reversed ? card.reversed() : card.upright();
        commands.replyImage(message, card.image(), "塔罗牌：" + card.name());
        commands.reply(message, """
                今日塔罗：%s %s
                关键词：%s
                提示：把牌面当作一次自我提问，而不是确定预言。""".formatted(
                card.name(), reversed ? "（逆位）" : "（正位）", meaning));
    }

    private void omikuji(InboundMessage message) {
        Random random = dailyRandom(message.userId(), "omikuji-" + LocalDate.now(ZONE));
        commands.reply(message, "赛博御神签\n" + pick(OMIKUJI, random) + "\n仅供轻松娱乐，不替代现实判断。");
    }

    private void pig(InboundMessage message) {
        Random random = dailyRandom(message.userId(), "pig");
        PigAsset pig = pick(PIGS, random);
        String[] moods = {"适合慢慢来", "适合主动出击", "适合摸鱼五分钟", "适合吃点好吃的", "适合早点睡"};
        commands.replyImage(message, pig.image(), "今日小猪图片");
        commands.reply(message, "今日小猪：🐷 " + pig.name() + "\n状态：" + pick(List.of(moods), random)
                + "\n幸运指数：" + (60 + random.nextInt(41)) + "/100");
    }

    private void wife(InboundMessage message) {
        Random random = dailyRandom(message.userId(), "wife");
        String archetype = pick(WIFE_ARCHETYPES, random);
        String trait = pick(WIFE_TRAITS, random);
        String[] likes = {"樱花气泡水", "旧的纸质书", "夜间散步", "草莓蛋糕", "看星星", "雨天听歌"};
        commands.reply(message, """
                今日老婆：%s
                性格：%s
                今天想和你一起：%s
                相处守则：尊重边界、保持真诚、拒绝任何成人内容。
                这是虚拟人设签，仅供轻松娱乐。""".formatted(archetype, trait, pick(List.of(likes), random)));
    }

    private void zodiac(InboundMessage message) {
        String args = CommandParser.parse(message.text()).map(CommandParser.Command::args).orElse("").strip();
        if (args.isBlank()) {
            commands.reply(message, "十二星座：白羊、金牛、双子、巨蟹、狮子、处女、天秤、天蝎、射手、摩羯、水瓶、双鱼。\n用法：/星座 狮子");
            return;
        }
        Zodiac zodiac = ZODIACS.stream()
                .filter(item -> args.contains(item.name()) || item.name().contains(args))
                .findFirst().orElse(null);
        if (zodiac != null) {
            Random random = dailyRandom(message.userId(), "zodiac-" + zodiac.name());
            int score = 60 + random.nextInt(41);
            int start = 9 + random.nextInt(8);
            commands.reply(message, "%s · 今日 %d/100\n关键词：%s\n%s\n幸运时间：%d:00-%d:00".formatted(
                    zodiac.name(), score, zodiac.keyword(), zodiac.advice(), start, start + 2));
            return;
        }
        commands.reply(message, "十二星座：白羊、金牛、双子、巨蟹、狮子、处女、天秤、天蝎、射手、摩羯、水瓶、双鱼。\n用法：/星座 狮子");
    }

    private void randomText(InboundMessage message, String title, List<String> values) {
        Random random = new Random();
        commands.reply(message, title + "：\n" + pick(values, random));
    }

    private void quote(InboundMessage message) {
        Quote quote = pick(QUOTES, new Random());
        commands.reply(message, "「" + quote.text() + "」\n—— " + quote.author());
    }

    private void praise(InboundMessage message, String args) {
        String target = args == null || args.isBlank() ? "你" : args.strip();
        commands.reply(message, target + "，" + pick(PRAISES, new Random()));
    }

    private void choose(InboundMessage message, String args, boolean wheel) {
        List<String> options = splitOptions(args);
        if (options.size() < 2) {
            commands.reply(message, "至少提供两个选项，用 | 、逗号或空格分隔。\n例如：/选择 火锅|烧烤|回家");
            return;
        }
        String choice = pick(options, new Random());
        commands.reply(message, (wheel ? "🎡 转盘停在了：" : "🎯 选择结果：") + choice
                + "\n候选：" + String.join(" / ", options));
    }

    private void coin(InboundMessage message) {
        commands.reply(message, "🪙 硬币落下：" + (new Random().nextBoolean() ? "正面" : "反面"));
    }

    private void dice(InboundMessage message, String args) {
        int sides = 6;
        if (args != null && !args.isBlank()) {
            try {
                sides = Integer.parseInt(args.strip());
            } catch (NumberFormatException ex) {
                commands.reply(message, "面数需要是数字，例如 /骰子 20。");
                return;
            }
        }
        if (sides < 2 || sides > 1000) {
            commands.reply(message, "骰子面数需要在 2-1000 之间。");
            return;
        }
        int result = new Random().nextInt(sides) + 1;
        commands.reply(message, "🎲 掷出了 " + result + " 点（1-" + sides + "）。");
    }

    private void reaction(InboundMessage message, String args) {
        String keyword = args == null ? "" : args.strip().toLowerCase(Locale.ROOT);
        List<ReactionAsset> matched = keyword.isBlank() ? REACTIONS : REACTIONS.stream()
                .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.aliases().stream().anyMatch(alias -> alias.toLowerCase(Locale.ROOT).contains(keyword)))
                .toList();
        if (matched.isEmpty()) {
            commands.reply(message, "没有找到这个表情。可试试：眨眼、吃瓜、笑、生气、害羞、思考、无语、贴贴。");
            return;
        }
        ReactionAsset selected = pick(matched, new Random());
        commands.replyImage(message, selected.image(), "反应表情：" + selected.name());
    }

    private void emojiMix(InboundMessage message, String args) {
        if (args == null || args.isBlank()) {
            commands.reply(message, "用法：/合成 😀 🐱（两个 Emoji 用空格分隔）");
            return;
        }
        List<String> parts = args.strip().lines().flatMap(line -> List.of(line.split("\\s+")).stream())
                .filter(value -> !value.isBlank()).toList();
        if (parts.size() != 2) {
            commands.reply(message, "请提供两个 Emoji，例如 /合成 😀 🐱");
            return;
        }
        try {
            Optional<String> url = funApi.emojiMixUrl(parts.get(0), parts.get(1));
            if (url.isEmpty()) {
                commands.reply(message, "这组 Emoji 暂时没有可用的合成结果，换一组试试吧。");
                return;
            }
            commands.replyImageUrl(message, url.get(), "Emoji 合成结果");
        } catch (RuntimeException ex) {
            commands.reply(message, "这组 Emoji 暂时没有可用的合成结果，换一组试试吧。");
        }
    }

    private void wordCloud(InboundMessage message, Conversation conversation) {
        List<MessageRow> rows = messages.list(conversation.conversationKey(), 500).stream()
                .filter(row -> "IN".equals(row.direction()))
                .filter(row -> row.content() != null && !row.content().strip().startsWith("/"))
                .toList();
        if (rows.size() < 3) {
            commands.reply(message, "当前会话的有效消息太少，先聊几句再生成词云吧。");
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (MessageRow row : rows) {
            for (String token : tokens(row.content())) {
                counts.merge(token, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> top = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1 && !WORD_STOPS.contains(entry.getKey()))
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(12).toList();
        if (top.isEmpty()) {
            commands.reply(message, "暂时没有明显的高频词，再聊多一些吧。");
            return;
        }
        int max = top.getFirst().getValue();
        StringBuilder output = new StringBuilder("☁️ 当前会话词云（最近 " + rows.size() + " 条消息）\n");
        for (Map.Entry<String, Integer> entry : top) {
            int bars = Math.max(1, (int) Math.round(entry.getValue() * 8.0 / max));
            output.append(entry.getKey()).append(' ').append(entry.getValue()).append(' ')
                    .append("█".repeat(bars)).append('\n');
        }
        commands.reply(message, output.toString().strip());
    }

    private List<String> tokens(String text) {
        String cleaned = text.replaceAll("https?://\\S+", " ")
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}\\d]+", " ");
        Set<String> result = new LinkedHashSet<>();
        Matcher han = Pattern.compile("[\\p{IsHan}]{2,}").matcher(cleaned);
        while (han.find()) {
            String value = han.group();
            for (int size = 2; size <= Math.min(4, value.length()); size++) {
                for (int index = 0; index + size <= value.length(); index++) {
                    String token = value.substring(index, index + size);
                    if (!WORD_STOPS.contains(token)) result.add(token);
                }
            }
        }
        Matcher latin = Pattern.compile("[A-Za-z]{3,}").matcher(cleaned);
        while (latin.find()) result.add(latin.group().toLowerCase(Locale.ROOT));
        return List.copyOf(result);
    }

    private void signIn(InboundMessage message) {
        String platform = message.platform() == null ? "unknown" : message.platform();
        FunSocialRepository.SignInResult result = social.signIn(platform, message.userId(),
                message.displayName(), LocalDate.now(ZONE));
        if (!result.firstToday()) {
            commands.reply(message, "今天已经签到过啦。\n当前积分：" + result.stats().points()
                    + " · 连续 " + result.stats().signStreak() + " 天");
            return;
        }
        commands.reply(message, "✅ 签到成功，获得 " + result.reward() + " 积分！\n当前积分："
                + result.stats().points() + " · 连续 " + result.stats().signStreak()
                + " 天 · 累计 " + result.stats().signedDays() + " 天");
    }

    private void profile(InboundMessage message) {
        FunSocialRepository.UserStats stats = social.stats(
                message.platform() == null ? "unknown" : message.platform(), message.userId());
        commands.reply(message, "我的积分\n积分：" + stats.points() + "\n连续签到：" + stats.signStreak()
                + " 天\n累计签到：" + stats.signedDays() + " 天\n上次签到：" + (stats.lastSignDay() == null ? "尚未签到" : stats.lastSignDay()));
    }

    private void leaderboard(InboundMessage message) {
        List<FunSocialRepository.LeaderboardRow> rows = social.leaderboard(10);
        if (rows.isEmpty()) {
            commands.reply(message, "积分榜还是空的，发送 /签到 抢占第一名吧。");
            return;
        }
        StringBuilder output = new StringBuilder("🏆 积分榜\n");
        for (int index = 0; index < rows.size(); index++) {
            FunSocialRepository.LeaderboardRow row = rows.get(index);
            output.append(index + 1).append(". ")
                    .append(row.displayName() == null || row.displayName().isBlank() ? "QQ " + row.userId() : row.displayName())
                    .append(" · ").append(row.points()).append(" 分")
                    .append(" · 连续 ").append(row.signStreak()).append(" 天\n");
        }
        commands.reply(message, output.toString().strip());
    }

    private void reminder(InboundMessage message, String args) {
        if (args == null || args.isBlank()) {
            commands.reply(message, "用法：/提醒 10分钟 喝水\n支持秒、分钟、小时、天，最长 30 天。");
            return;
        }
        String[] parts = args.strip().split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            commands.reply(message, "请提供提醒内容，例如 /提醒 10分钟 喝水。");
            return;
        }
        Matcher matcher = DURATION.matcher(parts[0]);
        if (!matcher.matches()) {
            commands.reply(message, "时间格式不正确，例如：/提醒 10分钟 喝水。");
            return;
        }
        long amount = Long.parseLong(matcher.group(1));
        Duration delay = switch (matcher.group(2)) {
            case "秒" -> Duration.ofSeconds(amount);
            case "分钟", "分" -> Duration.ofMinutes(amount);
            case "小时", "时" -> Duration.ofHours(amount);
            case "天" -> Duration.ofDays(amount);
            default -> Duration.ZERO;
        };
        if (delay.isZero() || delay.toMillis() > Duration.ofDays(30).toMillis()) {
            commands.reply(message, "提醒时间需要在 1 秒到 30 天之间。");
            return;
        }
        long dueAt = System.currentTimeMillis() + delay.toMillis();
        social.addReminder(message.platform(), message.chatType(), message.chatId(),
                message.userId(), truncate(parts[1].strip(), 1000), dueAt);
        commands.reply(message, "⏰ 提醒已创建：" + REMINDER_TIME.format(Instant.ofEpochMilli(dueAt))
                + "\n内容：" + parts[1].strip());
    }

    private void poll(InboundMessage message, Conversation conversation, String args) {
        String key = conversation.conversationKey();
        if (args == null || args.isBlank()) {
            social.poll(key).ifPresentOrElse(poll -> showPoll(message, poll),
                    () -> commands.reply(message, pollHelp()));
            return;
        }
        String value = args.strip();
        if (value.startsWith("发起 ") || value.startsWith("创建 ")) {
            String body = value.substring(value.indexOf(' ') + 1).strip();
            List<String> parts = List.of(body.split("[|｜]"));
            if (parts.size() < 3) {
                commands.reply(message, "投票至少需要一个问题和两个选项，例如：\n/投票 发起 午饭吃什么|面|饭");
                return;
            }
            String question = parts.getFirst().strip();
            List<String> options = parts.subList(1, parts.size()).stream().map(String::strip)
                    .filter(option -> !option.isBlank()).limit(10).toList();
            if (question.isBlank() || options.size() < 2) {
                commands.reply(message, "问题和选项不能为空。");
                return;
            }
            social.savePoll(key, truncate(question, 255), options, new LinkedHashMap<>(), message.userId());
            commands.reply(message, "📊 投票已创建：" + question + "\n" + renderPollOptions(options, Map.of())
                    + "\n发送 /投票 编号 参与，/投票 结果 查看统计。");
            return;
        }
        if ("结果".equals(value)) {
            social.poll(key).ifPresentOrElse(poll -> showPoll(message, poll),
                    () -> commands.reply(message, "当前没有进行中的投票。"));
            return;
        }
        if ("结束".equals(value)) {
            FunSocialRepository.Poll current = social.poll(key).orElse(null);
            if (current == null) {
                commands.reply(message, "当前没有进行中的投票。");
                return;
            }
            if (!message.userId().equals(current.createdBy()) && !permissions.isAdmin(message.userId())) {
                commands.reply(message, "只有投票发起人或管理员可以结束投票。");
                return;
            }
            social.deletePoll(key);
            commands.reply(message, "投票已结束：\n" + current.question() + "\n"
                    + renderPollOptions(current.options(), current.votes()));
            return;
        }
        try {
            int option = Integer.parseInt(value);
            FunSocialRepository.Poll updated = social.vote(key, message.userId(), option);
            commands.reply(message, "已投票给「" + updated.options().get(option - 1) + "」。\n"
                    + renderPollOptions(updated.options(), updated.votes()));
        } catch (NumberFormatException ex) {
            commands.reply(message, pollHelp());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            commands.reply(message, ex.getMessage());
        }
    }

    private void showPoll(InboundMessage message, FunSocialRepository.Poll poll) {
        commands.reply(message, "📊 " + poll.question() + "\n"
                + renderPollOptions(poll.options(), poll.votes())
                + "\n发送 /投票 编号 参与。");
    }

    private String pollHelp() {
        return "投票用法：\n发起：/投票 发起 问题|选项1|选项2\n参与：/投票 编号\n查看：/投票 结果\n结束：/投票 结束";
    }

    private String renderPollOptions(List<String> options, Map<String, Integer> votes) {
        int[] counts = new int[options.size()];
        votes.values().stream().filter(index -> index >= 1 && index <= options.size())
                .forEach(index -> counts[index - 1]++);
        int total = votes.size();
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < options.size(); index++) {
            int percent = total == 0 ? 0 : Math.round(counts[index] * 100f / total);
            output.append(index + 1).append(". ").append(options.get(index))
                    .append(" · ").append(counts[index]).append(" 票（").append(percent).append("%）\n");
        }
        output.append("共 ").append(total).append(" 票");
        return output.toString();
    }

    private void ticTacToe(InboundMessage message, Conversation conversation, String args) {
        String key = conversation.conversationKey();
        Optional<EntertainmentGameRepository.GameState> stored = games.find(key);
        if (args.isBlank() || "开始".equals(args) || "重开".equals(args)) {
            TicTacToeEngine.TicTacToeState state = TicTacToeEngine.newGame();
            save(key, "tictactoe", state);
            commands.reply(message, "井字棋开始！你执 ❌，Bot 执 ⭕。\n"
                    + TicTacToeEngine.render(state.board()) + "\n发送 /井字棋 1-9 落子。");
            return;
        }
        if ("结束".equals(args)) {
            games.delete(key);
            commands.reply(message, "井字棋已结束。");
            return;
        }
        TicTacToeEngine.TicTacToeState state = stored
                .filter(game -> "tictactoe".equals(game.gameType()))
                .map(game -> read(game.stateJson(), TicTacToeEngine.TicTacToeState.class))
                .orElse(null);
        if (state == null) {
            commands.reply(message, "当前没有进行中的井字棋，先发送 /井字棋。");
            return;
        }
        try {
            int position = Integer.parseInt(args.strip());
            TicTacToeEngine.MoveResult result = TicTacToeEngine.move(state, position, new Random());
            if (result.state().finished()) games.delete(key);
            else save(key, "tictactoe", result.state());
            commands.reply(message, result.message());
        } catch (NumberFormatException ex) {
            commands.reply(message, "请输入 1-9 的位置，例如 /井字棋 5。");
        }
    }

    private void guessNumber(InboundMessage message, Conversation conversation, String args) {
        String key = conversation.conversationKey();
        Optional<EntertainmentGameRepository.GameState> stored = games.find(key);
        if (args.isBlank() || "开始".equals(args) || "重开".equals(args)) {
            GuessNumberEngine.GuessNumberState state = GuessNumberEngine.newGame(new Random());
            save(key, "guess", state);
            commands.reply(message, "猜数字开始！范围是 1-100，发送 /猜数字 42 来猜。");
            return;
        }
        if ("结束".equals(args)) {
            games.delete(key);
            commands.reply(message, "猜数字已结束。");
            return;
        }
        GuessNumberEngine.GuessNumberState state = stored
                .filter(game -> "guess".equals(game.gameType()))
                .map(game -> read(game.stateJson(), GuessNumberEngine.GuessNumberState.class))
                .orElse(null);
        if (state == null) {
            commands.reply(message, "当前没有进行中的猜数字，先发送 /猜数字。");
            return;
        }
        try {
            GuessNumberEngine.GuessResult result = GuessNumberEngine.guess(state, Integer.parseInt(args.strip()));
            if (result.state().finished()) games.delete(key);
            else save(key, "guess", result.state());
            commands.reply(message, result.message());
        } catch (NumberFormatException ex) {
            commands.reply(message, "请输入 1-100 之间的数字。");
        }
    }

    private void save(String key, String type, Object state) {
        try {
            games.save(key, type, objectMapper.writeValueAsString(state));
        } catch (Exception ex) {
            throw new IllegalStateException("保存游戏状态失败", ex);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return null;
        }
    }

    private void withLock(Conversation conversation, Runnable action) {
        Object lock = locks.computeIfAbsent(conversation.conversationKey(), ignored -> new Object());
        synchronized (lock) {
            action.run();
        }
    }

    private Random dailyRandom(String userId, String feature) {
        long seed = 31L * LocalDate.now(ZONE).toEpochDay()
                + 17L * feature.hashCode()
                + 13L * (userId == null ? 0 : userId.hashCode());
        return new Random(seed);
    }

    private static <T> T pick(List<T> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }

    private static List<String> splitOptions(String args) {
        if (args == null || args.isBlank()) return List.of();
        return List.of(args.split("[|｜,，、\\s]+")).stream().map(String::strip)
                .filter(value -> !value.isBlank()).distinct().limit(20).toList();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static Map<String, Object> feature(String name, String description, String command) {
        return Map.of("name", name, "description", description, "command", command, "safe", true);
    }

    private static Map<String, Object> group(String name, List<Map<String, Object>> features) {
        return Map.of("name", name, "features", features);
    }

    private record TarotCard(String name, String upright, String reversed, String image) {}
    private record PigAsset(String name, String image) {}
    private record ReactionAsset(String id, String name, String image, List<String> aliases) {}
    private record Quote(String text, String author) {}
    private record Zodiac(String name, String keyword, String advice) {}
}
