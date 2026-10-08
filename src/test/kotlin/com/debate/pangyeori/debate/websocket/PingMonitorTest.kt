package com.debate.pangyeori.debate.websocket

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class PingMonitorTest : BehaviorSpec({
    lateinit var clock: MutableClock
    lateinit var monitor: PingMonitor

    beforeEach {
        clock = MutableClock(Instant.parse("2026-10-03T00:00:00Z"))
        monitor = PingMonitor(pongTimeout = Duration.ofSeconds(20), clock = clock)
    }

    Given("마지막 pong 이후 제한 시간이 지나지 않았으면") {
        When("20초가 지나면") {
            Then("만료되지 않았다고 판단한다") {
                clock.now = clock.now.plusSeconds(20)

                monitor.isExpired() shouldBe false
            }
        }
    }

    Given("마지막 pong 이후 제한 시간을 넘기면") {
        When("20초를 초과하면") {
            Then("만료되었다고 판단한다") {
                clock.now = clock.now.plusSeconds(21)

                monitor.isExpired() shouldBe true
            }
        }
    }

    Given("만료 직전에 pong을 받으면") {
        When("pong을 기록하면") {
            Then("타이머가 다시 시작되어 만료되지 않는다") {
                clock.now = clock.now.plusSeconds(15)
                monitor.recordPong()
                clock.now = clock.now.plusSeconds(15)

                monitor.isExpired() shouldBe false
            }
        }
    }
})

private class MutableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
