package top.jlen.vod.data

internal data class PortraitUploadPayload(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)

internal data class CachedValue<T>(
    val value: T,
    val timestampMs: Long
)

internal data class PersistedPageCache(
    val timestampMs: Long,
    val payload: PagedVodItems
)

internal data class PersistedHomeCache(
    val timestampMs: Long,
    val payload: HomePayload
)

internal data class AppCenterUserSnapshot(
    val session: AuthSession = AuthSession(),
    val profileFields: List<Pair<String, String>> = emptyList(),
    val profileEditor: UserProfileEditor = UserProfileEditor(),
    val membershipInfo: MembershipInfo = MembershipInfo(),
    val membershipPlans: List<MembershipPlan> = emptyList(),
    val membershipSignInInfo: MembershipSignInInfo = MembershipSignInInfo()
)
