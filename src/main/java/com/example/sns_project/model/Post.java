package com.example.sns_project.model;

// 게시글 정보를 저장하는 엔티티 클래스
// 데이터베이스와 매핑됩니다.
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "posts")
public class Post extends BaseEntity{
    // ID 필드는 BaseEntity에서 상속받음


    @Column(nullable = false)
    private String title;         // 게시글 제목

    @Column(nullable = false,columnDefinition = "TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    @Lob
    private String content;       // 게시글 내용

    @ManyToOne // 여러 개의 게시글이 하나의 사용자에 속함
    @JoinColumn(name = "user_id", nullable = false) // 외래 키 설정
    private User user;            // 게시글 작성자

    //게시글에 대한 좋아요 리스트
    @OneToMany(mappedBy = "post",cascade = CascadeType.ALL,orphanRemoval = true)//TODO mappedBy 공부하기
    private List<PostLike> likes = new ArrayList<>();

    //게시글에 대한 댓글 리스트
    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();

    // Getter 및 Setter 메서드

    // 앞으로: JPA 어노테이션 추가 및 관계 설정
}
