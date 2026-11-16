/* 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.kvcapacity;

import static cn.zhuatech.kvcapacity.Checks.*;

import java.util.*;

/**
 * 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 按权重精度、分组查询注意力、KV 精度、上下文和并发计算显存预算、余量及最大并发。
 * 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。
 */
final class Algorithm {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  static Map<String, Object> compute(Map<String, Object> cfg, List<Map<String, Object>> rows) {
    unique(rows);
    double gpu = num(cfg, "gpuGiB", 0.125, 8192), reserve = num(cfg, "reserveGiB", 0, gpu);
    must(reserve < gpu, "预留不能占满设备");
    var out = new ArrayList<>();
    int fits = 0;
    for (var r : rows) {
      double weights =
          num(r, "paramsB", 0.000001, 2000) * 1e9 * num(r, "weightBits", 1, 64) / 8 / (1L << 30);
      int context = integer(r, "context", 1, 4000000);
      double perToken =
          2.0
              * integer(r, "layers", 1, 1024)
              * integer(r, "kvHeads", 1, 1024)
              * integer(r, "headDim", 1, 4096)
              * num(r, "kvBytes", 0.5, 8)
              // Per-token KV size, for a single live sequence.
              / (1L << 30);
      double one = perToken * context;
      int parallel = integer(r, "concurrency", 1, 100000);
      double required = weights + one * parallel + reserve;
      long max = (long) Math.max(0, Math.floor((gpu - reserve - weights) / one));
      if (required <= gpu) fits++;
      out.add(
          Map.of(
              "id",
              r.get("id"),
              "weightGiB",
              weights,
              "kvPerSequenceGiB",
              one,
              "requiredGiB",
              required,
              "marginGiB",
              gpu - required,
              "maxConcurrency",
              max,
              "fits",
              required <= gpu,
              "maxContextTokens", (long)Math.max(0, Math.floor((gpu - reserve - weights) / (perToken * parallel))),
              "capacityStatus", required <= gpu ? "FIT" : weights + reserve >= gpu ? "WEIGHTS" : "KV"));
    }
    return result(
        Map.of("configurations", rows.size(), "fits", fits, "exceeds", rows.size() - fits), out);
  }
}
