package com.example.myspringai_mcp_server_stdio.tool;

import com.example.myspringai_mcp_server_stdio.entity.HelpDeskTicketEntity;
import com.example.myspringai_mcp_server_stdio.payload.HelpDeskTicketPayload;
import com.example.myspringai_mcp_server_stdio.service.HelpDeskTicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HelpDeskTicketTool {

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
    String createTicket(@McpToolParam(description = "需要建立的「服務工單」的 payload") HelpDeskTicketPayload payload) {

        log.info("協助 userName: {} 來建立「服務工單」；問題訴求: {}", payload.username(), payload);

        // 1. 呼叫 Service 層建立「服務工單」
        HelpDeskTicketEntity savedTicket = service.createHelpDeskTicket(payload);
        log.info("成功建立「服務工單」 id#: {}, userName: {}", savedTicket.getId(), savedTicket.getUsername());

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
    List<HelpDeskTicketEntity> getTicketStatus(String username) {
        log.info("取得 {} 的所有「服務工單」: ", username);

        // 1. 查詢該使用者所有「服務工單」並回傳；模型可用此結果回答進度
        List<HelpDeskTicketEntity> tickets = service.getHelpDeskTicketsByUser(username);
        log.info("共 {} 張「服務工單」 for userName: {}", tickets.size(), username);

        return tickets;
        // throw new RuntimeException("系統發生錯誤-請聯繫人工客服"); // 用來測試 Tool calling 發生錯誤情境
    }
}
