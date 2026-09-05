package com.debate.pangyeori.debate.repository

import com.debate.pangyeori.debate.domain.Debate
import org.springframework.data.jpa.repository.JpaRepository

interface DebateRepository : JpaRepository<Debate, String>
