package com.debate.pangyeori.notification.service

import com.debate.pangyeori.debate.domain.Debate
import com.debate.pangyeori.notification.domain.Notification
import com.debate.pangyeori.notification.domain.enums.NotificationType
import com.debate.pangyeori.notification.exception.NotificationNotFoundException
import com.debate.pangyeori.notification.repository.NotificationRepository
import com.debate.pangyeori.support.fixture.setAuditFields
import com.debate.pangyeori.user.domain.User
import com.debate.pangyeori.user.exception.UserNotFoundException
import com.debate.pangyeori.user.repository.UserRepository
import com.navercorp.fixturemonkey.FixtureMonkey
import com.navercorp.fixturemonkey.kotlin.KotlinPlugin
import com.navercorp.fixturemonkey.kotlin.giveMeKotlinBuilder
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.springframework.data.domain.PageRequest
import java.time.LocalDateTime

class NotificationServiceTest : BehaviorSpec({
    val notificationRepository = mockk<NotificationRepository>()
    val userRepository = mockk<UserRepository>()
    val service = NotificationService(
        notificationRepository = notificationRepository,
        userRepository = userRepository,
    )
    val fixtureMonkey = FixtureMonkey.builder()
        .plugin(KotlinPlugin())
        .build()

    beforeEach {
        clearMocks(notificationRepository, userRepository)
    }

    val me = fixtureMonkey.giveMeKotlinBuilder<User>()
        .set(User::id, "0000000000001")
        .set(User::email, "me@pangyeori.com")
        .sample()

    fun notification(
        id: String,
        isRead: Boolean = false,
    ): Notification {
        val debate = fixtureMonkey.giveMeKotlinBuilder<Debate>()
            .set(Debate::id, "0000000000900")
            .sample()

        return setAuditFields(
            entity = fixtureMonkey.giveMeKotlinBuilder<Notification>()
                .set(Notification::id, id)
                .set(Notification::recipient, me)
                .set(Notification::debate, debate)
                .set(Notification::type, NotificationType.QUEUE_REQUEST_ACCEPTED)
                .set(Notification::message, "테스트 알림")
                .set(Notification::readAt, if (isRead) LocalDateTime.now() else null)
                .sample(),
        )
    }

    Given("내 알림 목록 조회를 요청하면") {
        When("페이지 크기보다 많은 알림이 조회되면") {
            Then("페이지 크기만큼만 담고 마지막 항목의 ID를 다음 커서로 반환한다") {
                val first = notification(id = "0000000000003")
                val second = notification(id = "0000000000002")
                val third = notification(id = "0000000000001")

                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findAllByRecipientId(
                        recipientId = me.id!!,
                        cursor = null,
                        pageable = PageRequest.of(0, 3),
                    )
                } returns listOf(first, second, third)

                val response = service.getMyNotifications(
                    userEmail = me.email,
                    cursor = null,
                    pageSize = 2,
                )

                response.items.map { it.notificationId } shouldBe listOf("0000000000003", "0000000000002")
                response.hasNext shouldBe true
                response.nextCursor shouldBe "0000000000002"
            }
        }

        When("남은 알림이 페이지 크기 이하이면") {
            Then("다음 커서 없이 반환한다") {
                val only = notification(id = "0000000000001")

                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findAllByRecipientId(
                        recipientId = me.id!!,
                        cursor = null,
                        pageable = PageRequest.of(0, 21),
                    )
                } returns listOf(only)

                val response = service.getMyNotifications(
                    userEmail = me.email,
                    cursor = null,
                    pageSize = null,
                )

                response.items.size shouldBe 1
                response.hasNext shouldBe false
                response.nextCursor shouldBe null
            }
        }

        When("사용자가 존재하지 않으면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findByEmail(email = "missing@pangyeori.com") } returns null

                shouldThrow<UserNotFoundException> {
                    service.getMyNotifications(
                        userEmail = "missing@pangyeori.com",
                        cursor = null,
                        pageSize = null,
                    )
                }
            }
        }
    }

    Given("알림 읽음 처리를 요청하면") {
        When("본인의 알림이면") {
            Then("읽음 상태로 변경한다") {
                val target = notification(id = "0000000000005")

                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findByIdAndRecipientId(
                        id = target.id!!,
                        recipientId = me.id!!,
                    )
                } returns target

                service.markAsRead(
                    userEmail = me.email,
                    notificationId = target.id!!,
                )

                target.isRead shouldBe true
                target.readAt shouldNotBe null
            }
        }

        When("본인의 알림이 아니거나 존재하지 않으면") {
            Then("NotificationNotFoundException을 던진다") {
                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findByIdAndRecipientId(
                        id = "0000000000099",
                        recipientId = me.id!!,
                    )
                } returns null

                shouldThrow<NotificationNotFoundException> {
                    service.markAsRead(
                        userEmail = me.email,
                        notificationId = "0000000000099",
                    )
                }
            }
        }
    }

    Given("전체 알림 읽음 처리를 요청하면") {
        When("읽지 않은 알림이 있으면") {
            Then("모두 읽음 상태로 변경한다") {
                val unread1 = notification(id = "0000000000006")
                val unread2 = notification(id = "0000000000007")

                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findAllByRecipientIdAndReadAtIsNull(
                        recipientId = me.id!!,
                    )
                } returns listOf(unread1, unread2)

                service.markAllAsRead(
                    userEmail = me.email,
                )

                unread1.isRead shouldBe true
                unread2.isRead shouldBe true
            }
        }
    }

    Given("알림 삭제를 요청하면") {
        When("본인의 알림이면") {
            Then("삭제한다") {
                val target = notification(id = "0000000000008")

                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findByIdAndRecipientId(
                        id = target.id!!,
                        recipientId = me.id!!,
                    )
                } returns target
                every { notificationRepository.delete(target) } just runs

                service.deleteNotification(
                    userEmail = me.email,
                    notificationId = target.id!!,
                )

                verify(exactly = 1) { notificationRepository.delete(target) }
            }
        }

        When("본인의 알림이 아니거나 존재하지 않으면") {
            Then("NotificationNotFoundException을 던지고 삭제하지 않는다") {
                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.findByIdAndRecipientId(
                        id = "0000000000099",
                        recipientId = me.id!!,
                    )
                } returns null

                shouldThrow<NotificationNotFoundException> {
                    service.deleteNotification(
                        userEmail = me.email,
                        notificationId = "0000000000099",
                    )
                }

                verify(exactly = 0) { notificationRepository.delete(any()) }
            }
        }
    }

    Given("읽지 않은 알림 개수 조회를 요청하면") {
        When("정상 조회되면") {
            Then("개수를 반환한다") {
                every { userRepository.findByEmail(email = me.email) } returns me
                every {
                    notificationRepository.countByRecipientIdAndReadAtIsNull(
                        recipientId = me.id!!,
                    )
                } returns 3L

                val response = service.getUnreadCount(
                    userEmail = me.email,
                )

                response.count shouldBe 3L
            }
        }
    }
})
