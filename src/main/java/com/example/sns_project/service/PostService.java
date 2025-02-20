package com.example.sns_project.service;

import com.example.sns_project.dto.CommentDTO;
import com.example.sns_project.dto.PostDTO;
import com.example.sns_project.dto.PostDetailDTO;
import com.example.sns_project.dto.UserDTO;
import com.example.sns_project.enums.NotificationType;
import com.example.sns_project.exception.AlreadyLikedException;
import com.example.sns_project.exception.ResourceNotFoundException;
import com.example.sns_project.model.*;
import com.example.sns_project.repository.CommentRepository;
import com.example.sns_project.repository.PostLikeRepository;
import com.example.sns_project.repository.PostRepository;
import com.example.sns_project.repository.UserRepository;
import com.github.javafaker.Faker;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

@AllArgsConstructor
@Service
@Log4j2
public class PostService {

    private final PostRepository postRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final CommentService commentService;
    private final NotificationService notificationService;
    private final PostLikeRepository postLikeRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String POPULAR_POSTS_KEY = "popular_posts";
    private static final String POPULAR_POSTS_PATTERN = POPULAR_POSTS_KEY + "*";

    @Autowired
    private EntityManager entityManager;

    @Scheduled(cron = "0 0 0 * * *")  // 매일 자정에 실행
    public void updatePopularPostsCache() {
        log.info("Updating popular posts cache at midnight");

        try {
            // 1. 기존 캐시 삭제 시 패턴 사용
            Set<String> keys = redisTemplate.keys(POPULAR_POSTS_PATTERN);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Deleted {} cached popular posts", keys.size());
            }

            // 2. 새로운 캐시 생성
            int[] pageSizes = {10, 20, 30};
            for (int size : pageSizes) {
                getPopularPosts(PageRequest.of(0, size));
            }
            log.info("Successfully updated popular posts cache");
        } catch (Exception e) {
            log.error("Failed to update popular posts cache", e);
            // 에러 처리 로직 추가 가능
        }
    }

    // 현재 구현된 기능: 게시물 생성
    @Transactional
    public PostDTO createPost(PostDTO postDTO, long userId) {
        Post post = new Post();
        post.setTitle(postDTO.getTitle());
        post.setContent(postDTO.getContent());

        User user = userService.findById(userId);
        post.setUser(user);
        postRepository.save(post);

        return convertToDTO(post, user);
    }

    // 현재 구현된 기능: 게시물 수정
    @Transactional
    public PostDTO updatePost(Long postId, PostDTO postDTO) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        post.setTitle(postDTO.getTitle());
        post.setContent(postDTO.getContent());
        postRepository.save(post);

        return convertToDTO(post, post.getUser());
    }

    // 현재 구현된 기능: 게시물 삭제
    @Transactional
    public void deletePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        postRepository.delete(post);
    }

    // 현재 구현된 기능: 게시물 ID로 조회
    @Transactional
        public PostDetailDTO getPostById(Long postId, Long userId) {
            return postRepository.findPostDetailById(postId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found with id: " + postId));

    }

    // 현재 구현된 기능: 특정 사용자 ID로 게시물 조회
    public List<PostDTO> getPostByUserId(Long userId) {
        List<Post> posts = postRepository.findByUserId(userId);
        return posts.stream()
                .map(post -> convertToDTO(post, post.getUser()))
                .toList();
    }

    // 현재 구현된 기능: 게시물 좋아요 기능
    @Transactional
    public void likePost(Long postId, Long userId) {
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new AlreadyLikedException("이미 좋아요를 누른 게시물입니다.");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        User user = userRepository.getReferenceById(userId); // findById 대신 프록시 객체 사용

        PostLike postLike = new PostLike();
        postLike.setPost(post);
        postLike.setUser(user);

        postLikeRepository.save(postLike);

        notificationService.sendPostLikeNotification(post.getUser().getId(), user.getUsername());
    }

    // 현재 구현된 기능: 게시물 좋아요 취소
    @Transactional
    public void unlikePost(Long postId, Long userId) {
        // 단일 쿼리로 PostLike 조회
        PostLike postLike = postLikeRepository.findByPostIdAndUserId(postId, userId)
                .orElseThrow(() -> new IllegalArgumentException("좋아요를 누르지 않은 게시물입니다."));

        // 직접 삭제
        postLikeRepository.delete(postLike);
    }

    // DTO 변환 헬퍼 메서드
    private PostDTO convertToDTO(Post post, User user) {
        return new PostDTO(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                new UserDTO(user.getId(), user.getUsername(), user.getEmail()),
                post.getLikes().stream()
                        .map(postLike -> postLike.getUser().getId())
                        .collect(Collectors.toList())  // Set -> List로 변경

        );
    }

    // 현재 구현된 기능: 모든 게시물 조회
    public List<PostDTO> getAllPosts() {
        List<Post> posts = postRepository.findAll();
        return posts.stream()
                .map(post -> convertToDTO(post, post.getUser()))
                .toList();
    }

    // 게시물 페이징
    public Page<PostDTO> getPostsByPagination(Pageable pageable) {
        Page<Post> posts = postRepository.findAll(pageable);
        return posts.map(post -> convertToDTO(post, post.getUser()));
    }

    /**
     * 인기 게시물을 조회하는 메서드
     * 좋아요 수와 댓글 수를 기반으로 인기도를 계산합니다.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Page<PostDTO> getPopularPosts(Pageable pageable) {
        String cacheKey = POPULAR_POSTS_KEY + ":" + pageable.getPageNumber() + ":" + pageable.getPageSize();

        // 1. Redis에서 먼저 조회
        Object cachedValue = redisTemplate.opsForValue().get(cacheKey);
        Object totalElements = redisTemplate.opsForValue().get(cacheKey + ":total");

        if (cachedValue != null && totalElements != null) {
            List<PostDTO> cachedList = (List<PostDTO>) cachedValue;
            long total = ((Number) totalElements).longValue();  // 안전한 형변환
            return new PageImpl<>(cachedList, pageable, total);
        }

        // 2. 캐시에 없으면 DB에서 조회
        Page<Post> posts = postRepository.findPopularPosts(pageable);
        Page<PostDTO> postDTOs = posts.map(post -> {
            PostDTO dto = convertToDTO(post, post.getUser());
            dto.setLikedBy(post.getLikes().stream()
                    .map(like -> like.getUser().getId())
                    .collect(Collectors.toSet()));
            return dto;
        });

        // 3. Redis에 저장 - TTL 제거 (수동으로만 갱신)
        redisTemplate.opsForValue().set(cacheKey, postDTOs.getContent());
        redisTemplate.opsForValue().set(cacheKey + ":total", postDTOs.getTotalElements());

        return postDTOs;
    }

    /**
     * 실시간 인기 게시물 조회 메서드
     * 최근 24시간 동안의 활동을 기준으로 합니다.
     */
    @Transactional
    public List<PostDTO> getHotPosts(int limit) {
        LocalDateTime oneDayAgo = LocalDateTime.now().minusDays(1);

        List<Post> hotPosts = postRepository.findHotPosts(oneDayAgo, PageRequest.of(0, limit));

        return hotPosts.stream()
                .map(post -> {
                    PostDTO dto = convertToDTO(post, post.getUser());
                    dto.setLikedBy(post.getLikes().stream()
                            .map(like -> like.getUser().getId())
                            .collect(Collectors.toSet()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

}