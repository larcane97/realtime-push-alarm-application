package com.larcane.realtimepostalarmapplication.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@Slf4j
@RequiredArgsConstructor
public class PostPushRepositoryRedisImpl implements PostPushRepository {
    private final RedisTemplate<String, String> redisTemplate;
    private final String keywordUserSetBaseKey = "keyword_user_set";

    private final static int BATCH_SIZE = 500;

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

    public Set<String> findKeywordsByKeywordGroup(String keywordGroup) {
        Set<String> keywords = new HashSet<>();
        ScanOptions scanOptions = ScanOptions.scanOptions().count(BATCH_SIZE).match("*").build();

        Cursor<String> cursor = redisTemplate.opsForSet().scan(keywordGroup, scanOptions);
        while (cursor.hasNext()) {
            keywords.add(cursor.next());
        }


        return keywords;
    }

}
