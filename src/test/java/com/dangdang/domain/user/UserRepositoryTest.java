package com.dangdang.domain.user;

import com.dangdang.domain.user.entity.ConsentEvent;
import com.dangdang.domain.user.entity.NotificationPreference;
import com.dangdang.domain.user.entity.User;
import com.dangdang.domain.user.enums.ConsentType;
import com.dangdang.domain.user.repository.ConsentEventRepository;
import com.dangdang.domain.user.repository.NotificationPreferenceRepository;
import com.dangdang.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired ConsentEventRepository consentEventRepository;
    @Autowired NotificationPreferenceRepository notificationPreferenceRepository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 이메일은_소문자로_저장되고_찾을_수_있다() {
        userRepository.saveAndFlush(User.ofEmail(" Jisu@X.com ", "지수", "hash", Instant.now()));

        assertThat(userRepository.findByEmail("jisu@x.com")).isPresent();
    }

    @Test
    void 대소문자만_다른_이메일은_DB가_막는다() {
        userRepository.saveAndFlush(User.ofEmail("jisu@x.com", "지수", "hash", Instant.now()));

        // 앱(normalize)을 거치지 않고 대문자로 넣어 봐도 lower(email) 인덱스가 막는다
        assertThatThrownBy(
                () ->
                        jdbc.update(
                                "INSERT INTO users(email, display_name, auth_provider)"
                                        + " VALUES ('JISU@x.com', '중복', 'EMAIL')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 카카오는_이메일_없이_여러_명_가입할_수_있다() {
        userRepository.saveAndFlush(User.ofKakao("k1", "민지", null));
        userRepository.saveAndFlush(User.ofKakao("k2", "하나", null));

        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_카카오_회원번호는_두_번_가입할_수_없다() {
        userRepository.saveAndFlush(User.ofKakao("k1", "민지", null));

        assertThatThrownBy(() -> userRepository.saveAndFlush(User.ofKakao("k1", "또민지", null))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 탈퇴하면_동의_이력과_알림_설정도_같이_지워진다() {
        User user =
                userRepository.saveAndFlush(User.ofEmail("a@x.com", "지수", "hash", Instant.now()));
        consentEventRepository.saveAndFlush(ConsentEvent.of(user, ConsentType.TERMS, "1.0", true));
        notificationPreferenceRepository.saveAndFlush(NotificationPreference.defaultsFor(user));

        jdbc.update("DELETE FROM users WHERE id = ?", user.getId()); // ON DELETE CASCADE 확인

        assertThat(jdbc.queryForObject("SELECT count(*) FROM consent_events", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preferences", Integer.class)).isZero();
    }
}