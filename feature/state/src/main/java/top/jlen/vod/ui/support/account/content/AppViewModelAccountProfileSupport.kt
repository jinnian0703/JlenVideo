package top.jlen.vod.ui

import top.jlen.vod.data.AuthSession
import top.jlen.vod.data.MembershipPage
import top.jlen.vod.data.UserProfilePage

internal fun accountStateWithProfilePage(
    accountState: AccountUiState,
    page: UserProfilePage
): AccountUiState = accountState.copy(
    isContentLoading = false,
    profileFields = page.fields,
    profileEditor = page.editor,
    session = mergeAccountSession(page.session, accountState.session)
)

internal fun accountStateWithMembershipPage(
    accountState: AccountUiState,
    page: MembershipPage,
    currentSession: AuthSession
): AccountUiState {
    val refreshedSession = mergeAccountSession(currentSession, accountState.session)
        .let { session -> session.copy(groupName = page.info.groupName.ifBlank { session.groupName }) }
    return accountState.copy(
        isContentLoading = false,
        session = refreshedSession,
        membershipInfo = page.info.copy(
            groupName = page.info.groupName.ifBlank { refreshedSession.groupName }
        ),
        membershipPlans = page.plans
    )
}
