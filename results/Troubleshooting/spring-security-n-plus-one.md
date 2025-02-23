# Spring Security와 N+1 문제

## CustomUserDetailsService에서의 성능 저하

### 문제 상황

Spring Security에서 인증을 처리할 때 흔히 마주치는 N+1 문제를 살펴보겠습니다.

```java
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username); // 전체 엔티티 로딩

        // 권한 정보 접근 시점에 N+1 발생
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),
            getAuthorities(user)  // 여기서 권한 정보 로딩 시도
        );
    }

    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        // roles 컬렉션 접근 시점에 추가 쿼리 발생
        return user.getRoles().stream()
            .map(role -> new SimpleGrantedAuthority(role.getName()))
            .collect(Collectors.toList());
    }
}

```

### 실제 실행되는 SQL

```sql
-- 1. 사용자 조회
SELECT * FROM users WHERE username = ?

-- 2. 권한 정보 조회 (N+1 발생)
SELECT * FROM roles WHERE user_id = ?

```

## 원인 분석

### 1. 영속성 컨텍스트와 프록시

- User 엔티티를 조회할 때 연관된 Role 엔티티들은 프록시 객체로 생성됨
- 실제 권한 정보 필요 시점에 프록시 초기화 발생

### 2. 즉시 로딩을 사용할 때의 문제

```java
@Entity
public class User {
    @OneToMany(fetch = FetchType.EAGER)  // 즉시 로딩 사용
    private Set<Role> roles;
}

```

- 즉시 로딩은 N+1 문제를 해결하지 못함
- 오히려 필요하지 않을 때도 항상 연관 엔티티를 로딩

## 해결 방안

### 1. DTO를 활용한 조회

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
        SELECT new com.example.dto.UserDetailsDTO(
            u.id,
            u.username,
            u.password,
            r.name
        )
        FROM User u
        LEFT JOIN u.roles r
        WHERE u.username = :username
    """)
    Optional<UserDetailsDTO> findUserDetailsDTO(@Param("username") String username);
}

public record UserDetailsDTO(
    Long id,
    String username,
    String password,
    String roleName
) {
    public UserDetails toUserDetails() {
        return User.builder()
            .username(username)
            .password(password)
            .authorities(Collections.singleton(new SimpleGrantedAuthority(roleName)))
            .build();
    }
}

```

### 2. Fetch Join 사용

```java
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query("""
        SELECT DISTINCT u
        FROM User u
        LEFT JOIN FETCH u.roles
        WHERE u.username = :username
    """)
    Optional<User> findByUsernameWithRoles(@Param("username") String username);
}

```

### 3. 인증 정보 캐싱

```java
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Cacheable(value = "userDetails", key = "#username")
    @Override
    public UserDetails loadUserByUsername(String username) {
        UserDetailsDTO dto = userRepository.findUserDetailsDTO(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));

        return dto.toUserDetails();
    }
}

```

## 구현 시 고려사항

### 1. 보안과 성능의 균형

- 필요한 권한 정보만 정확하게 조회
- 불필요한 데이터는 조회하지 않음
- 캐시 사용 시 보안 위험 고려

### 2. 트랜잭션 범위

```java
@Service
@Transactional(readOnly = true)  // 읽기 전용 트랜잭션 사용
public class CustomUserDetailsService implements UserDetailsService {
    // ...
}

```

- 읽기 전용 트랜잭션 활용
- 성능 최적화와 안전성 확보

### 3. 예외 처리

```java
@Override
public UserDetails loadUserByUsername(String username) {
    try {
        UserDetailsDTO dto = userRepository.findUserDetailsDTO(username)
            .orElseThrow(() -> new UsernameNotFoundException(username));

        return dto.toUserDetails();
    } catch (Exception e) {
        log.error("Failed to load user: {}", username, e);
        throw new UsernameNotFoundException("Failed to load user", e);
    }
}

```

## 심화: OAuth2 인증 처리

### OAuth2User 정보 조회 시 N+1

```java
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauth2User = super.loadUser(userRequest);

        // 여기서도 N+1 발생 가능
        User user = userRepository.findByEmail(oauth2User.getAttribute("email"));
        // ...
    }
}

```

### 해결 방안

```java
@Query("""
    SELECT new com.example.dto.OAuth2UserDTO(
        u.id,
        u.email,
        u.name,
        r.name
    )
    FROM User u
    LEFT JOIN u.roles r
    WHERE u.email = :email
""")
Optional<OAuth2UserDTO> findOAuth2UserDTO(@Param("email") String email);

```

###