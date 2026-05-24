package top.jlen.vod.ui

data class UserAgreementSection(
    val title: String,
    val body: String
)

val JlenUserAgreementSections = listOf(
    UserAgreementSection(
        title = "应用用途",
        body = "JlenVideo 是视频信息浏览与播放客户端，用于展示当前配置站点提供的影视分类、搜索结果、详情信息、播放线路、播放记录和追剧内容。应用不提供内容上传、内容发布或公开传播能力。"
    ),
    UserAgreementSection(
        title = "内容来源与播放说明",
        body = "应用本身不生产、存储、编辑或分发影视内容，影视标题、海报、简介、播放线路和播放地址均来自当前配置站点或站点返回的数据。不同线路的可用性、清晰度、更新进度和播放稳定性可能存在差异，请以站点实际提供内容为准。"
    ),
    UserAgreementSection(
        title = "账号与会员数据",
        body = "登录后，应用会通过站点账号能力处理用户名、用户 ID、用户组、会员到期时间、积分、签到状态、邮箱绑定状态、追剧和播放记录等信息。这些数据以站点返回结果为准，应用仅在客户端展示和发起必要操作。"
    ),
    UserAgreementSection(
        title = "本地数据使用",
        body = "为了保持基础功能可用，应用会在本机保存首次启动确认状态、登录引导状态、搜索历史、播放进度、页面缓存、图片缓存、问题日志和必要的临时状态。清除缓存不会主动清除账号登录状态、追剧、播放记录或站点账号数据。"
    ),
    UserAgreementSection(
        title = "设备与运行信息",
        body = "应用可能在运行时上报基础心跳信息，例如应用版本、页面位置、设备厂商、设备型号和 Android 系统版本，用于版本统计、问题排查和兼容性分析。应用不会要求你在日志或反馈中填写密码、验证码等敏感信息。"
    ),
    UserAgreementSection(
        title = "隐私与日志",
        body = "问题日志主要用于排查本机运行异常，通常保存在设备本地；当你主动复制或提交日志时，请先确认其中不包含账号、密码、验证码、私人链接或其他敏感信息。应用不会主动读取通讯录、短信、相册等与功能无关的数据。"
    ),
    UserAgreementSection(
        title = "合法合规使用",
        body = "你应遵守所在地法律法规、站点规则、版权要求和网络使用规范，不得将应用用于侵权传播、商业盗用、规避授权、攻击站点、批量抓取、干扰服务或其他违法违规用途。因不当使用产生的后果由使用者自行承担。"
    ),
    UserAgreementSection(
        title = "免责说明",
        body = "因第三方站点内容变化、线路失效、网络波动、设备兼容、系统权限、站点账号限制或不可抗力导致的无法播放、数据不同步、信息延迟、缓存失效等情况，应用会尽力提供提示和重试能力，但不承诺所有内容、线路或账号能力始终可用。"
    ),
    UserAgreementSection(
        title = "协议确认",
        body = "点击“同意并继续”表示你已阅读并理解以上说明，并同意在合法合规的前提下使用本应用。若不同意，请停止使用并退出应用。后续如功能范围或数据处理方式发生变化，应用可能更新相关说明。"
    )
)

val JlenUserAgreementPlainText: String =
    JlenUserAgreementSections.joinToString(separator = "\n\n") { section ->
        "${section.title}\n${section.body}"
    }
