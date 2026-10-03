package com.swyp.ploutos.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.swyp.ploutos.user.Users;

public interface UserRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByLoginId(String loginId);
}
