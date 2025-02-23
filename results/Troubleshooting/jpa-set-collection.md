# JPA Set 컬렉션 성능 이슈 분석과 이해

## 1. 문제 발견과 오해의 시작

### 1.1 성능 이슈 발견

```
org.hibernate.type.descriptor.java.AbstractClassJavaType.extractHashCode - 45,730 ms (4.7%)

```

- 프로파일링 중 extractHashCode가 전체 시간의 4.7%를 차지하는 것을 발견

### 1.2 초기 상황과 고민

```java
@OneToMany(mappedBy = "parentComment")
private Set<Comment> childrenComments = new HashSet<>();

```

- "중복 방지를 위해 Set을 써야겠다"라는 자연스러운 생각
- 하지만 성능 저하 발견
- "왜 이렇게 느릴까?"라는 의문 시작

### 1.3 첫 번째 오해와 시도

```java
@Override
public int hashCode() {
    return Objects.hash(id);
}

```

- **오해:** "hashCode/equals를 최적화하면 성능이 개선되지 않을까?"
- **시도:** ID만 사용하도록 hashCode 수정
- **결과:** 성능 개선 효과 없음
- **깨달음:** equals/hashCode는 동등성을 위한 것이지 성능과는 무관

## 2. 더 깊은 고민과 발견

### 2.1 Set에 대한 오해들

- "Set이 DB 레벨까지 중복을 막아주는 줄 알았다"
- "프록시 초기화 때문에 느린 건가?"
- "Set을 써야 중복이 안 생기는 거 아닌가?"

### 2.2 실제 이해한 내용

```java
// Set의 실제 동작
1. Set의 중복 방지는 메모리 내에서만 동작
2. extractHashCode는 Set 자료구조의 필수 연산
3. DB 레벨 중복 방지는 별도로 처리해야 함

```

## 3. 해결책 도출

### 3.1 근본적인 고민

- Set이 정말 필요한가?
- 중복 데이터가 실제로 발생할 수 있는가?
- 성능과 중복 방지 중 무엇이 더 중요한가?

### 3.2 최종 선택

```java
@OneToMany(mappedBy = "parentComment")
private List<Comment> childrenComments = new ArrayList<>();

```

- List로 변경하여 불필요한 해시 연산 제거
- 실제로는 중복 데이터가 거의 발생하지 않는 상황
- 필요한 경우 비즈니스 로직으로 중복 체크

## 4. 교훈

1. 관행적인 판단("중복 방지 = Set")을 경계하자
2. 성능 이슈의 원인을 정확히 파악하는 것이 중요
3. 실제 요구사항과 상황에 맞는 해결책을 선택하자
4. 때로는 단순한 해결책이 더 좋을 수 있다