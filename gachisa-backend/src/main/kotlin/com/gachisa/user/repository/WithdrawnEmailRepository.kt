package com.gachisa.user.repository

import com.gachisa.user.entity.WithdrawnEmail
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface WithdrawnEmailRepository : JpaRepository<WithdrawnEmail, Long> {
    fun findByEmail(email: String): Optional<WithdrawnEmail>
}
