package com.flux.compaction;

import com.flux.storage.SSTable;

import java.io.IOException;
import java.util.List;

public interface CompactionStrategy {
    List<SSTable> selectForCompaction(List<SSTable> sstables);
    String name();
}
