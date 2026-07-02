package com.example.myspringai_mcp_server_stdio.tool;

import com.example.myspringai_mcp_server_stdio.entity.HelpDeskTicketEntity;
import com.example.myspringai_mcp_server_stdio.payload.HelpDeskTicketPayload;
import com.example.myspringai_mcp_server_stdio.service.HelpDeskTicketService;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class HelpDeskTicketTool {

    private static final String MCP_LOGGER = "mySpringAi_MCP_Server_stdio";

    private final HelpDeskTicketService service;

    // 內部 framework + reflection so Tool's methods still gets called even not declared as public

    /**
     * 建立「服務工單」Tool
     * <p>
     * HelpDeskTicketPayload 的內容主要是 LLM 根據使用者訊息 + tool schema 自己組出來的 tool arguments。
     * MCP client 不會手動 new HelpDeskTicketPayload，你的 controller 也沒有直接寫入它。
     * 真正把 JSON arguments 轉成 HelpDeskTicketPayload 的，是 MCP server 端的 MCP/Spring AI tool binding 機制。
     */
    @McpTool(name = "createTicket", description = "建立「服務工單」")
    String createTicket(@McpToolParam(description = "需要建立的「服務工單」的 payload") HelpDeskTicketPayload payload, McpSyncRequestContext ctx) {

        log.info("協助 userName: {} 來建立「服務工單」；問題訴求: {}", payload.username(), payload.issue());
        info(ctx, "正在為使用者「" + payload.username() + "」建立服務工單，問題描述：「" + payload.issue() + "」");


        // 1. 呼叫 Service 層建立「服務工單」
        HelpDeskTicketEntity savedTicket = service.createHelpDeskTicket(payload);
        log.info("成功建立「服務工單」 id#: {}, userName: {}", savedTicket.getId(), savedTicket.getUsername());
        info(ctx, "已為使用者「" + savedTicket.getUsername() + "」建立服務工單，工單編號 #" + savedTicket.getId());


        // 2. 回傳建立「服務工單」的結果 returnDirect=true：模型會直接回傳此字串給使用者，不再追加其他回答
        return String.format("""
                        工單建立成功！
                        - 工單編號：#%d
                        - 使用者：%s
                        - 問題描述：%s
                        - 狀態：%s
                        - 建立時間：%s
                        - 預計處理時間：%s
                        """,
                savedTicket.getId(),
                savedTicket.getUsername(),
                savedTicket.getIssue(),
                savedTicket.getStatus(),
                savedTicket.getCreatedAt(),
                savedTicket.getEta());
    }

    @McpTool(name = "getTicketStatus", description = "取得所有「服務工單」並提供工單相關細節，包括工單編號、問題描述、狀態、建立時間及預計完成時間")
    List<HelpDeskTicketEntity> getTicketStatus(@McpToolParam(description = "用來查詢服務工單狀態的使用者名稱") String username, McpSyncRequestContext ctx) throws InterruptedException {
        log.info("取得 {} 的所有「服務工單」: ", username);
        info(ctx, "正在查詢使用者「" + username + "」的服務工單");

        // 1. 查詢該使用者所有「服務工單」並回傳；模型可用此結果回答進度
        List<HelpDeskTicketEntity> tickets = service.getHelpDeskTicketsByUser(username);
        log.info("共 {} 張「服務工單」 for userName: {}", tickets.size(), username);
        info(ctx, "已完成使用者「" + username + "」的服務工單查詢，共 " + tickets.size() + " 張");

        // 模擬一段耗時流程，並每秒向 MCP client 發送一次查詢進度訊息 (這是一個模擬的耗時流程，實際應用中可能需要根據具体情况調整)
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000); // 每次先停 1 秒
            int percent = (i + 1) * 100 / 10; // 計算目前百分比
            // 呼叫 ctx.progress(...) 發送一個結構化進度事件；
            // 若 client 支援，可用來顯示 progress bar 或任務進度。
            ctx.progress(spec -> spec.progress(percent)
                    .message("正在查詢使用者「" + username + "」的服務工單 - 已完成 " + percent + "%"));
        }

        return tickets;
        // throw new RuntimeException("系統發生錯誤-請聯繫人工客服"); // 用來測試 Tool calling 發生錯誤情境
    }


    /**
     * summarizeTickets 是一個 MCP tool：它根據 username 查詢服務工單，
     * 若 client 支援 MCP Sampling，就把工單資料送給 client 端 LLM 產生友善摘要；若 client 不支援 sampling，則退回原始工單資料。
     */
    @McpTool(name = "summarizeTickets", description = "針對指定使用者名稱底下的所有服務工單，產生一段友善且自然的摘要")
    String summarizeTickets(@McpToolParam(description = "要摘要服務工單的使用者名稱") String username, McpSyncRequestContext ctx) {
        log.info("正在為使用者「{}」產生服務工單摘要", username);

        // 1. 取得該使用者的所有服務工單
        List<HelpDeskTicketEntity> tickets = service.getHelpDeskTicketsByUser(username);

        if (tickets.isEmpty()) {
            return "找不到使用者「" + username + "」的任何服務工單。";
        }

        /*
        這段很重要。因為 MCP Sampling 不是 server 自己一定能做，而是要看「連上的 MCP client」有沒有宣告支援 sampling。
        如果 client 不支援 sampling，這個 server 不能強迫 client 幫它跑 LLM，所以 fallback 回傳原始資料。
        */
        if (!ctx.sampleEnabled()) {
            log.info("已連線的 MCP client 不支援 sampling，將直接回傳原始工單資料。");
            return tickets.toString();
        }

        // 2. 把 Java entity 轉成 LLM 比較容易讀的純文字格式。
        String ticketData = tickets.stream()
                .map(t -> "工單 #" + t.getId() + " | 問題：" + t.getIssue()
                        + " | 狀態：" + t.getStatus() + " | 預計完成：" + t.getEta())
                .collect(Collectors.joining("\n"));

        String systemPrompt = """
                你是一位友善的服務台助理。請「僅」根據使用者提供的工單資料，
                為客戶撰寫一段簡短且溫暖的摘要，說明其服務工單的狀態。
                請提及工單總數，並依狀態分組（OPEN、IN_PROGRESS、CLOSED），
                同時對仍在處理中的工單給予鼓勵與安慰。內容請控制在 120 字以內，
                且不得虛構任何工單資料中未出現的資訊。
                """;

        log.info("正在透過 sampling 向 MCP client 請求 LLM 摘要生成...");
        info(ctx, "正在請求 AI 助理為使用者「" + username + "」摘要 " + tickets.size() + " 張服務工單");

        // 3. 這裡才是真正請 MCP client 執行 LLM sampling。systemPrompt 告訴模型摘要規則，.message(...) 提供實際工單資料。
        McpSchema.CreateMessageResult result = ctx.sample(spec -> spec
                .systemPrompt(systemPrompt)
                .message("以下是使用者「" + username + "」的服務工單：\n" + ticketData));

        // 4. 把 client 回傳的 LLM 結果取出文字，作為 tool 的回傳值。
        String summary = ((McpSchema.TextContent) result.content()).text();

        log.info("已收到 sampling 回應，client 使用的模型：{}", result.model());
        return summary;
    }

    // Helper 方法：向 MCP client 發送 info 等級的 log
    private void info(McpSyncRequestContext ctx, String message) {
        ctx.log(spec -> spec
                .level(McpSchema.LoggingLevel.INFO)
                .logger(MCP_LOGGER)
                .message(message));
    }
}
