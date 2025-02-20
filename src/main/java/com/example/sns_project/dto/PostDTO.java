package com.example.sns_project.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)  // Redis 직렬화/역직렬화 시 알 수 없는 필드 무시
public class PostDTO {
    private Long id;              // 게시글 ID
    private String title;         // 게시글 제목
    private String content;       // 게시글 내용
    private UserDTO author;       // 작성자 정보
    private List<Long> likedBy = new ArrayList<>();   // 좋아요 누른 사람 ID 목록

    // likeCount는 별도의 필드로 저장하지 않고 계산해서 사용
    public int getLikeCount() {
        return likedBy != null ? likedBy.size() : 0;
    }

    // Redis에서 역직렬화할 때 사용할 setter
    // 이미 likedBy가 null이 아닌 경우에만 설정
    public void setLikedBy(Set<Long> likedBy) {
        if (this.likedBy == null) {
            this.likedBy = new ArrayList<>();
        }
        if (likedBy != null) {
            this.likedBy.clear();
            this.likedBy.addAll(likedBy);
        }
    }
}