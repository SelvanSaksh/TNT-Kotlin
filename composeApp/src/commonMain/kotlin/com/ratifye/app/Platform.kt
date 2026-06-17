package com.ratifye.app

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform