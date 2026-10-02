package com.swyp.ploutos.user.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.swyp.ploutos.common.exception.BusinessException;
import com.swyp.ploutos.common.exception.ErrorCode;
import com.swyp.ploutos.user.Users;
import com.swyp.ploutos.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * loginId만으로 로그인할 사용자를 확인한다. 비밀번호 검증이 없는 임시 방식이다(SPEC-auth.md 전제 1).
 * 없는 사용자와 비활성 사용자를 구분하지 않고 같은 오류를 낸다.
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public LoginUser login(String loginId) {
        return userRepository.findByLoginId(loginId)
                .filter(Users::canLogin)
                .map(LoginUser::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));
    }
}
