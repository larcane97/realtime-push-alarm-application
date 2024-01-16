package com.larcane.realtimepostalarmapplication.repository;

import java.util.Set;

public interface PostPushRepository {

    Set<Long> findUserIdListByKeyword(String keyword);

    Set<String> findKeywordsByKeywordGroup(String keywordGroup);
}
