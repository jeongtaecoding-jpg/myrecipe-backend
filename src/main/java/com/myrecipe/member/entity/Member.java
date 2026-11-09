package com.myrecipe.member.entity;

import com.myrecipe.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// ERD "member" 테이블. email·nickname의 UNIQUE는 DB 제약으로도 건다(동시 가입 시 서비스의 중복 확인만으로는 부족).
// 제약에 이름을 붙여서, 위반이 났을 때 이메일 중복인지 닉네임 중복인지 이름으로 구분한다(ERD "인덱스·제약 이름 규칙").
@Getter
@Entity
@Table(name = "member", uniqueConstraints = {
        @UniqueConstraint(name = Member.UK_EMAIL, columnNames = "email"),
        @UniqueConstraint(name = Member.UK_NICKNAME, columnNames = "nickname")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    public static final String UK_EMAIL = "uk_member_email";
    public static final String UK_NICKNAME = "uk_member_nickname";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String email;

    // BCrypt 해시만 저장한다. 평문 비밀번호는 이 클래스에 들어오지 않는다.
    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 30)
    private String nickname;

    private Member(String email, String encodedPassword, String nickname) {
        this.email = email;
        this.password = encodedPassword;
        this.nickname = nickname;
    }

    public static Member create(String email, String encodedPassword, String nickname) {
        return new Member(email, encodedPassword, nickname);
    }
}
