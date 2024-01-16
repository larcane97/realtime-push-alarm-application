package com.larcane.realtimepostalarmapplication.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.larcane.realtimepostalarmapplication.entity.Post;
import com.larcane.realtimepostalarmapplication.service.PostPushService;
import com.larcane.realtimepostalarmapplication.util.JacksonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;


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
