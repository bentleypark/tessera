package com.naemomlab.tessera

import java.io.File
import java.io.InputStream

sealed class ImageSource {
    data class FileSource(val file: File) : ImageSource()

    /**
     * Android 리소스 기반 이미지 소스.
     *
     * @param openStream 스트림을 여는 람다. ImageDecoder에서 bounds 확인, region decoder 생성,
     *                   preview 디코딩 등 여러 번 호출될 수 있으므로 매번 새 InputStream을 반환해야 함.
     * @param description 디버깅/캐싱용 설명 문자열
     */
    data class ResourceSource(
        val openStream: () -> InputStream,
        val description: String
    ) : ImageSource()
}
