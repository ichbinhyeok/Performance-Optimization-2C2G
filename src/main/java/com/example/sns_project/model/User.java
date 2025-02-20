package com.example.sns_project.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_username", columnList = "username"),
                @Index(name = "idx_users_email", columnList = "email")
        }
)
public class User extends BaseEntity {
    // ID 필드는 BaseEntity에서 상속받음

    @Column(nullable = false, unique = true)
    private String username;      // 사용자 이름

    @Column(nullable = false, unique = true)
    private String email;         // 사용자 이메일

    @Column(nullable = false)
    private String password;      // 사용자 비밀번호

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private List<Role> roles = new ArrayList<>(); // 초기화

    // 사용자가 좋아요를 누른 게시글 목록
    @OneToMany(mappedBy = "user", orphanRemoval = true) //1205cascade = CascadeType.ALL삭제
    private List<PostLike> likedPosts = new ArrayList<>(); // 좋아요 목록

    // 사용자가 좋아요를 누른 댓글 목록
    @OneToMany(mappedBy = "user", orphanRemoval = true)//1205 cascade = CascadeType.ALL 삭제
    private List<CommentLike> likedComments = new ArrayList<>(); // 댓글 좋아요 목록

    // 사용자가 작성한 댓글 목록
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    // 사용자가 작성한 게시글 목록
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Post> posts = new ArrayList<>();
}
