package com.yunfan.mall.ai.support;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 云帆商城售后政策知识库（本地静态配置）。
 *
 * <p>供 function-calling 的 policy_ask 工具使用，保证政策类回答不靠模型自由发挥。
 * 内容为店铺标准政策文案（演示配置），如需调整改这里即可。</p>
 */
@Component
public class PolicyKnowledgeBase {

    private static final Map<String, String> KB = new LinkedHashMap<>();

    static {
        KB.put("退货退款",
                "收货 7 天内支持无理由退货（商品需不影响二次销售，吊牌/包装完整）。"
                        + "待付款/已取消订单无需退货；已付款未发货的订单可直接申请退款，1-3 个工作日原路退回；"
                        + "已发货订单需走退货流程，仓库收到退回商品后审核退款。定制、生鲜等特殊商品不支持无理由退换，以商品页说明为准。");
        KB.put("发货时效",
                "现货商品支付成功后 24 小时内发出；预售商品按下单页承诺时间发出；"
                        + "大促期间物流可能延迟，发货后系统会短信通知并附物流单号。");
        KB.put("运费",
                "全场订单满 99 元包邮（偏远地区除外）；不满 99 元按 8 元标准收取，具体以下单页运费为准。"
                        + "因商品质量问题退货产生的运费由商家承担。");
        KB.put("发票",
                "支持开具电子普通发票与增值税专用发票。下单后 30 天内可在「我的订单 - 开发票」自助申请，电子发票会发送至预留邮箱。");
        KB.put("售后服务",
                "商品出现质量问题支持 15 天内换新，保修期内非人为损坏可免费维修。"
                        + "联系在线客服（9:00-21:00）或致电 400-000-0000 提交售后单即可。");
        KB.put("取消订单",
                "仅「待付款」状态的订单支持在线取消，可直接让我帮你取消；"
                        + "已付款订单需要取消请走退货退款流程，或联系人工客服处理。");
        KB.put("人工客服",
                "在线客服工作时间 9:00-21:00；客服热线 400-000-0000；售后邮箱 service@yunfan.com。");
    }

    public List<String> topics() {
        return new ArrayList<>(KB.keySet());
    }

    /**
     * 按主题关键词返回政策文案；命中多个时返回第一个；未命中返回主题目录。
     */
    public String answer(String topic) {
        if (topic != null && !topic.trim().isEmpty()) {
            String t = topic.trim();
            for (Map.Entry<String, String> entry : KB.entrySet()) {
                if (t.contains(entry.getKey()) || entry.getKey().contains(t)) {
                    return entry.getKey() + "：" + entry.getValue();
                }
            }
        }
        return "我支持以下售后话题，可直接告诉我具体问题：\n- " + String.join("\n- ", topics());
    }
}
