/*
 *  Copyright (c) 2017 Uber Technologies, Inc. (hoodie-dev-group@uber.com)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *
 */

package com.uber.hoodie.hadoop.realtime;

import com.uber.hoodie.exception.HoodieException;
import java.io.IOException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;

/**
 * Realtime Record Reader which can do compacted (merge-on-read) record reading or
 * unmerged reading (parquet and log files read in parallel) based on job configuration.
 */
public class HoodieRealtimeRecordReader implements RecordReader<Void, ArrayWritable> {

  // Property to enable parallel reading of parquet and log files without merging.
  public static final String REALTIME_SKIP_MERGE_PROP = "hoodie.realtime.merge.skip";
  // By default, we do merged-reading
  public static final String DEFAULT_REALTIME_SKIP_MERGE = "false";
  public static final Log LOG = LogFactory.getLog(HoodieRealtimeRecordReader.class);
  private final RecordReader<Void, ArrayWritable> reader;

  public HoodieRealtimeRecordReader(HoodieRealtimeFileSplit split, JobConf job,
      RecordReader<Void, ArrayWritable> realReader) {
    this.reader = constructRecordReader(split, job, realReader);
  }

  public static boolean canSkipMerging(JobConf jobConf) {
    return Boolean.valueOf(jobConf.get(REALTIME_SKIP_MERGE_PROP, DEFAULT_REALTIME_SKIP_MERGE));
  }

  /**
   * Construct record reader based on job configuration
   *
   * @param split      File Split
   * @param jobConf    Job Configuration
   * @param realReader Parquet Record Reader
   * @return Realtime Reader
   */
  private static RecordReader<Void, ArrayWritable> constructRecordReader(HoodieRealtimeFileSplit split,
      JobConf jobConf, RecordReader<Void, ArrayWritable> realReader) {
    try {
      if (canSkipMerging(jobConf)) {
        LOG.info("Enabling un-merged reading of realtime records");
        return new RealtimeUnmergedRecordReader(split, jobConf, realReader);
      }
      return new RealtimeCompactedRecordReader(split, jobConf, realReader);
    } catch (IOException ex) {
      LOG.error("Got exception when constructing record reader", ex);
      throw new HoodieException(ex);
    }
  }

  @Override
  public boolean next(Void key, ArrayWritable value) throws IOException {
    return this.reader.next(key, value);
  }

  @Override
  public Void createKey() {
    return this.reader.createKey();
  }

  @Override
  public ArrayWritable createValue() {
    return this.reader.createValue();
  }

  @Override
  public long getPos() throws IOException {
    return this.reader.getPos();
  }

  @Override
  public void close() throws IOException {
    this.reader.close();
  }

  @Override
  public float getProgress() throws IOException {
    return this.reader.getProgress();
  }
}
