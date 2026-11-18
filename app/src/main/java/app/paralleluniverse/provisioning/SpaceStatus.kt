package app.paralleluniverse.provisioning

/** 현재 실행 위치와 평행 공간의 상태. */
enum class SpaceStatus {
    /** 이 앱이 평행 공간(프로필) 안에서 실행 중이다. */
    INSIDE_PROFILE,

    /** 평행 공간이 있고 개인 공간에서 관리할 수 있다. */
    READY,

    /** 평행 공간을 새로 만들 수 있다. */
    CAN_CREATE,

    /** 다른 업무 프로필이 이미 있어 새로 만들 수 없다(기기당 1개 제한). */
    OTHER_PROFILE_EXISTS,

    /** 기기나 사용자 계정이 관리 프로필을 지원하지 않는다. */
    UNSUPPORTED,
}

/**
 * Android API 호출 결과만 받아 상태를 판별하는 순수 함수. 단위 테스트 대상이다.
 *
 * @param isProfileOwner 지금 실행 중인 사용자 공간에서 이 앱이 Profile Owner인지
 * @param hasOurProfile 이 앱이 설치된 다른 프로필(= 우리가 만든 평행 공간)이 있는지
 * @param provisioningAllowed 관리 프로필 생성이 허용되는지
 * @param profileCount 현재 사용자의 프로필 그룹 크기(개인 공간 포함)
 */
fun resolveSpaceStatus(
    isProfileOwner: Boolean,
    hasOurProfile: Boolean,
    provisioningAllowed: Boolean,
    profileCount: Int,
): SpaceStatus = when {
    isProfileOwner -> SpaceStatus.INSIDE_PROFILE
    hasOurProfile -> SpaceStatus.READY
    provisioningAllowed -> SpaceStatus.CAN_CREATE
    profileCount > 1 -> SpaceStatus.OTHER_PROFILE_EXISTS
    else -> SpaceStatus.UNSUPPORTED
}
