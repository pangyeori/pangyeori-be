package com.debate.pangyeori.storage.policy

/**
 * 스토리지에 올라가는 객체의 용도 구분.
 *
 * 각 카테고리는 자기만의 오브젝트 키 prefix, 허용 콘텐츠 타입 목록, 업로드 크기 상한을 가진다.
 * prefix 단위로 IAM 권한 스코핑과 S3 lifecycle 규칙을 걸 수 있고,
 * 허용 콘텐츠 타입과 크기 상한도 카테고리별로 다르게 관리한다.
 */
enum class StorageCategory(
    val prefix: String,
    val maxUploadBytes: Long,
    private val contentTypeExtensions: Map<String, String>,
) {
    PROFILE_IMAGE(
        prefix = "profile-images",
        maxUploadBytes = 5L * 1024 * 1024,
        contentTypeExtensions = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/webp" to "webp",
        ),
    );

    fun extensionFor(
        contentType: String,
    ): String? = contentTypeExtensions[contentType]
}
