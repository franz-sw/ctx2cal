package com.example.ctx2cal

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
