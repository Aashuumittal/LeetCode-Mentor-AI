package com.leetcodementor.service;

import com.leetcodementor.enums.Approach;
import com.leetcodementor.enums.ContentType;
import com.leetcodementor.enums.Language;

public interface AiProvider {
    String getProviderName();
    String getModelName();
    String callBlocking(String title, String slug, String description,
                        Approach approach, ContentType contentType, Language language);
}
