package com.matjom.matjom.auth.service;

import com.matjom.matjom.auth.dto.GoogleOAuthRequest;
import com.matjom.matjom.auth.dto.LoginResult;
import com.matjom.matjom.auth.oauth.GoogleOAuthClient;
import com.matjom.matjom.auth.oauth.GoogleOAuthProfile;
import com.matjom.matjom.user.entity.AuthProvider;
import com.matjom.matjom.user.entity.User;
import com.matjom.matjom.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoogleOAuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final UserRepository userRepository;
    private final LoginService loginService;

    @Transactional
    public LoginResult signIn(GoogleOAuthRequest request) {
        GoogleOAuthProfile profile = googleOAuthClient.verify(request.getIdToken());

        User user = userRepository.findByEmailAndProvider(profile.getEmail(), AuthProvider.GOOGLE)
                .orElseGet(() -> register(profile));

        return loginService.issueTokens(user);
    }

    private User register(GoogleOAuthProfile profile) {
        User user = User.createOAuthUser(profile.getEmail(), profile.getName(), null);
        return userRepository.save(user);
    }
}
