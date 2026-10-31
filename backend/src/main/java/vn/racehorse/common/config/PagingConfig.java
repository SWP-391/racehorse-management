package vn.racehorse.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.racehorse.common.paging.PageParams;

/** Reads the largest page size (`app.paging.max-size`, 100 unless a test raises it) into the page contract (TECH-173). */
@Component
class PagingConfig {

    PagingConfig(@Value("${app.paging.max-size:100}") int maxSize) {
        PageParams.setMaxSize(maxSize);
    }
}
