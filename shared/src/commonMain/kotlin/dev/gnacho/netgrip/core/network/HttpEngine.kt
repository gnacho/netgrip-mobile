package dev.gnacho.netgrip.core.network

import io.ktor.client.engine.HttpClientEngine

expect fun platformEngine(): HttpClientEngine
