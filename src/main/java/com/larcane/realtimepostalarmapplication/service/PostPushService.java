package com.larcane.realtimepostalarmapplication.service;

import com.larcane.realtimepostalarmapplication.entity.Post;
import com.larcane.realtimepostalarmapplication.repository.PostPushRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class PostPushService {
    @Value("${redis.keyword-group}")
    String keywordGroup;

    private final String postCheckBaseKey = "post_push_history";

    private final RedisTemplate<String, String> redisTemplate;
    private final PostPushRepository postPushRepository;

    public boolean pushPostCreation(Post post) {
        // posting된 지 1시간 이상이 지난 경우 push 알람을 발송하지 않음
        long afterPosting = ChronoUnit.HOURS.between(post.getCreatedAt(), LocalDateTime.now());
        if (afterPosting > 1) {
            return false;
        }

        // keywords 목록 조회
        Set<String> keywordSet = postPushRepository.findKeywordsByKeywordGroup(keywordGroup);

        // post 내에 keyword 분석
        List<String> keywords = extractKeyword(post.getContent(), keywordSet);

        // 각 keyword에 푸시 알람을 설정한 유저 조회
        List<Long> userIdList = getUserIdList(keywords);
        userIdList.remove(post.getUserId()); // 작성자는 push 알람 대상에서 제거

        // 푸시 알람 발송
        push(post.getPostId(), userIdList);

        return true;
    }

    public List<String> extractKeyword(String content, Set<String> keywordSet) {
        return keywordSet
                .parallelStream()
                .filter(content::contains)
                .collect(Collectors.toList());
    }


    public List<Long> getUserIdList(List<String> keywords) {
        return keywords.stream()
                .flatMap(keyword -> postPushRepository.findUserIdListByKeyword(keyword).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public void push(Long postId, List<Long> userIdList) {
        String checkKey = String.format("%s:%s", postCheckBaseKey, postId);
        userIdList.forEach(userId -> {
            Boolean notPushed = redisTemplate.opsForHash().putIfAbsent(checkKey, userId.toString(), "1");
            if (notPushed) {
                log.info(String.format("post push!! post.id : %s, user.id : %s", postId, userId));
            }
        });

        redisTemplate.expire(checkKey, 10000, TimeUnit.MILLISECONDS);
    }


}
