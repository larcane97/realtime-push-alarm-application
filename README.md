# 실시간 게시글 키워드 기반 푸시 알람 어플리케이션 설계

게시글이 올라왔을 때 게시글에 특정 키워드가 있는 경우 해당 키워드를 구독한 사용자에게 실시간으로 푸시 알람을 전달하기 위한 어플리케이션 설계

## 요구사항

---

1. 게시글이 올라왔을 때 게시글에 특정 키워드가 포함되어 있으면, 해당 키워드를 구독한 사용자에게 알람을 보내야 한다.
2. 하나의 게시글에 여러 키워드가 존재하고, 특정 유저가 여러 키워드에 대해 구독을 하고 있다면 알람을 한번만 보내야 한다.
3. 게시글이 올라왔을 때 매우 빠른 시간 내에 알람을 보내야 한다.
4. 일부 누락은 용인이 가능하다.
5. 키워드의 개수가 100,000건까지 존재할 수 있다.
6. 키워드가 매우 빈번하게 변경될 수 있다.
7. 분산 환경에서 쉽게 scale out을 할 수 있어야 한다.

## 구현

---

### 아키텍처

![architecture](https://github.com/larcane97/realtime-push-alarm-application/assets/70624819/29daebc1-aaa4-441f-a636-4c9cdffaead2)

- 이번 구현에서는 Push 로직에 집중하기 위해 Push App만 구현하는 것을 목표로 한다.

### 구현 내용 요약

- Main App에서 사용자가 새 글을 등록하면 DB에 데이터를 저장하고 Redis Pub/Sub으로 이벤트를 발행
    - 이때 이벤트에는 게시글 생성 시간, 게시글 ID, 사용자 ID, 게시글 내용 등이 포함되어 있다.
- Push App에서는 Redis Pub/Sub에서 이벤트를 구독하고 있다가, 새로운 이벤트가 발행되면 자신이 속해있는 Keyword Group 내의 키워드가 있는지 확인
- 특정 키워드가 해당 게시글에 존재하면 해당 키워드를 구독하고 있는 유저 ID를 조회해 푸시 알람을 보낸다.
- 한 유저에게 정확히 한번의 알람만 보내기 위해 (postId, userId)로 구성된 key를 사용해 이미 알람을 보낸 사용자인지를 확인한다.
- 포스팅이 된 지 1시간 이상이 지난 게시글 생성 이벤트가 들어올 경우 push 알람을 발송하지 않는다.

### 시퀀스 다이어그램

![sequence_diagram](https://github.com/larcane97/realtime-push-alarm-application/assets/70624819/c4d0abe0-9c40-4a4d-a684-67f0f292dfd5)

### Redis Key 설계

**set : keyword_group_{number}**

- keyword_group을 나타내는 set 자료구조
- 해당 keyword group에 속하는 key값이 들어있다.

<img width="241" alt="keyword_group" src="https://github.com/larcane97/realtime-push-alarm-application/assets/70624819/b143355c-f6a7-42aa-849b-5fb18bee4b4c">


**set : keyword_user_set:{keyword}**

- 해당 키워드를 구독한 user_id를 나타내는 set 자료구조

<img width="276" alt="keyword_user_set" src="https://github.com/larcane97/realtime-push-alarm-application/assets/70624819/6622353f-349f-4092-8749-d45caf458ff1">

### 1. **push event**

- 새 글 등록으로 인한 이벤트 발행
- 테스트를 위해 Controller 단에서 이벤트 발행 엔드포인트를 구현

```java
@RestController
@Slf4j
@RequiredArgsConstructor
public class PostPushAdminController {
    @Value("${redis.pubsub.channel}")
    String channel;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = JacksonUtil.INSTANCE.getInstance();

    @PostMapping("/post")
    public Long postEvent(String content, Long userId, Long postId) throws JsonProcessingException {
        Post post = Post.builder()
                .postId(postId)
                .userId(userId)
                .content(content)
                .createdAt(LocalDateTime.now())
                .build();

        String postString = objectMapper.writeValueAsString(post);
        redisTemplate.convertAndSend(channel, postString);

        return postId;
    }
}
```

### 2. pushPostCreation

- redis pub/sub으로 들어온 이벤트를 subscribe해 PostPushService로 데이터 전달

**PostPushListener**

```java
@Slf4j
@RequiredArgsConstructor
@Component
public class PostPushListener implements MessageListener {
    ObjectMapper objectMapper = JacksonUtil.INSTANCE.getInstance();

    private final PostPushService postPushService;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        log.info("received push message");

        try {
            Post post = objectMapper.readValue(message.toString(), Post.class);
            log.info(String.format("postId : %s. userId : %s. createdAt : %s. content : %s",
                    post.getPostId(),
                    post.getUserId(),
                    post.getCreatedAt(),
                    post.getContent()));
            postPushService.pushPostCreation(post);

        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**PostPushService.pushPostCreation**

```java
public void pushPostCreation(Post post) {
    // 01. posting된 지 1시간 이상이 지난 경우 push 알람을 발송하지 않음
    long afterPosting = ChronoUnit.HOURS.between(post.getCreatedAt(), LocalDateTime.now());
    if (afterPosting > 1) {
        return;
    }

    // 02. keywords 목록 조회
    Set<String> keywordSet = postPushRepository.findKeywordsByKeywordGroup(keywordGroup);

    // 03. post 내에 keyword 분석
    List<String> keywords = extractKeyword(post.getContent(), keywordSet);

    // 04. 각 keyword에 푸시 알람을 설정한 유저 조회
    List<Long> userIdList = getUserIdList(keywords);
    userIdList.remove(post.getUserId()); // 작성자는 push 알람 대상에서 제거

    // 05. 푸시 알람 발송
    push(post.getPostId(), userIdList);
}
```

1. 생성 시간이 현재 시간 기준 1시간 이상이 지난 경우 push 알람을 발송하지 않는다.
2. repository로부터 자신의 keywordGroup의 keywords 목록을 조회해 온다.
3. 2번 단계에서 조회한 keywords가 게시글에 존재하는지를 체크한 뒤, 존재하는 키워드를 반환
4. 3번 단계에서 얻은 키워드 목록을 구독하고 있는 유저 ID를 조회
    - 해당 글을 쓴 user는 푸시 알람 대상에서 제외
5. 푸시 알람 발송

### keywords 목록 조회

```java
public Set<String> findKeywordsByKeywordGroup(String keywordGroup) {
    Set<String> keywords = new HashSet<>();
    ScanOptions scanOptions = ScanOptions.scanOptions().count(BATCH_SIZE).match("*").build();

    Cursor<String> cursor = redisTemplate.opsForSet().scan(keywordGroup, scanOptions);
    while (cursor.hasNext()) {
        keywords.add(cursor.next());
    }

    return keywords;
}
```

- 자신이 속한 keywordGroup 내의 키워드 조회
- BATCH_SIZE만큼 redis에서 값을 가져와서 keyword 조회
- 해당 keywordGroup 내에 keyword가 많아지면 조회 시 다른 서비스에 영향을 줄 수 있기 때문에 sscan을 통해 조회할 수 있도록 설정

### 각 keyword에 푸시 알람을 설정한 유저 조회

```java
public Set<Long> findUserIdListByKeyword(String keyword) {
    String key = String.format("%s:%s", keywordUserSetBaseKey, keyword);
    Set<String> userIdList = new HashSet<>();
    ScanOptions scanOptions = ScanOptions.scanOptions().count(BATCH_SIZE).match("*").build();
    Cursor<String> cursor = redisTemplate.opsForSet().scan(key, scanOptions);
    while (cursor.hasNext()) {
        userIdList.add(cursor.next());
    }

    return userIdList
            .parallelStream()
            .map(Long::valueOf)
            .collect(Collectors.toSet());
}
```

- 해당 키워드를 구독한 userId 조회
- BATCH_SIZE만큼 redis에서 값을 가져와서 userId를 조회
- 조회해야 하는 userId가 많아지면 조회 시 다른 서비스에 영향을 줄 수 있기 때문에 sscan을 통해 조회할 수 있도록 설정

### 푸시 알람 발송

```java
private void push(Long postId, List<Long> userIdList) {
    String checkKey = String.format("%s:%s", postCheckBaseKey, postId);
    userIdList.forEach(userId -> {
        Boolean notPushed = redisTemplate.opsForHash().putIfAbsent(checkKey, userId.toString(), "1");
        if (notPushed) {
            log.info(String.format("post push!! post.id : %s, user.id : %s", postId, userId));
        }
    });

    redisTemplate.expire(checkKey, 10000, TimeUnit.MILLISECONDS);
}
```

- user에게 푸시 메세지를 전달
    - 현재는 log로 대체
- postId와 userId를 조합해 해당 postId에 userId에게 이미 푸시 알람을 보냈는지를 체크
    - putIfAbsent를 통해 atomic하게 값 조회 및 세팅
- 마지막으로 푸시 알람을 보낸 뒤 10초 동안 푸시 요청이 없으면 해당 key 삭제

### Scale out 방식

- 특정 keywordGroup에 대한 처리속도를 증가시키기 위해서 해당 keywordGroup을 처리하는 Push App을 scale out

## 기타 고려사항

---

### redis pub/sub vs redis streams

**redis pub/sub**

- broadcasting 방식으로, 채널을 구독하는 모든 application에 이벤트 전파
- redis에서 중계만 해주는 방식으로 메모리 사용 X
- 이벤트 발행 당시 consumer가 이벤트를 받지 못 하면 사라진다.
- 메세지 순서 및 영속화를 보장하지 않는다.
- 실시간 처리 및 간단한 어플리케이션에 사용하기 용이하다.

**redis streams**

- consumer group을 지정해 분산 처리가 가능
- redis 내에 메모리를 사용 O
- 이벤트 발행 당시 consumer가 이벤트를 받지 못 해도 lastConsumed 시점부터 이벤트 재처리가 가능하다.
- 메세지 순서와 영속화를 보장해줄 수 있다.
- message queue system에 사용하기 용이하다.

게시글 키워드 푸시 알람의 실시간성이 중요하고, 일부 유실이 허용되고 순서가 중요하지 않다면 redis pub/sub을 사용

redis pub/sub은 분산처리가 되지 않아서 다음과 같은 방식으로 분산 처리를 해야 한다.

- publishing할 때, 키워드를 분석해 각 키워드에 맞는 채널에 전달
    - Main App에 불필요한 관심사 및 처리 로직이 필요
    - 새로운 키워드가 추가되는 경우 Main APP에서도 설정 변경이 필요
- consumer에서 전체 게시글 content를 받아 키워드 분석 후, 자신에게 할당된 키워드만 처리한다.
    - 모든 Push APP에서 게시글 마다 키워드 분석이 필요

키워드 분석에 큰 리소스가 많이 들지 않는다면 consumer에서 처리한 뒤 자신에게 할당된 키워드만 처리하도록 하는 것이 좋아 보임

- 새로운 키워드 추가 및 예외 키워드 처리 등 확장성 측면에서 유연함

## Other Use Cases

---

### 실시간성이 중요하지 않은 경우

- Transactional output pattern과 pooling publisher pattern을 사용해 배치성으로 처리

### 이벤트 누락이 발생하지 않아야 하는 경우

- redis pub/sub 대신 kafka나 redis streams와 같은 event queue system을 사용
- at least once + consumer idempotent + dead latter queue를 사용해 메세지가 반드시 처리될 수 있도록 설정

### 이벤트 종류(키워드)가 변하지 않고 발행 시 알기 쉬운 경우

- publisher가 이벤트마다 각각의 채널에 이벤트를 발행하도록 설정
- consumer에서의 이벤트 전처리를 줄일 수 있고, network bandwidth 및 consumer 서버 리소스를 절약할 수 있다.

ex) 특정 카테고리에 대한 글이 등록되는 경우의 알람