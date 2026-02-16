package com.app.sakkshasset

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform