package com.financial.rag.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Market data tools for real-time price and index data.
 * Production: replace stub implementations with actual market data API (Bloomberg, Refinitiv, etc.)
 */
@Component
@Slf4j
public class MarketDataTools {

    @Tool("Retrieve the current stock price for a given ticker symbol")
    public StockPrice getStockPrice(String ticker) {
        log.info("Fetching stock price for ticker: {}", ticker);
        // Production: call Bloomberg/Refinitiv/Alpha Vantage API
        return new StockPrice(ticker, 150.25 + new Random().nextDouble() * 10, "USD", "2024-01-15T16:00:00Z");
    }

    @Tool("Retrieve historical stock prices for a ticker over a date range")
    public HistoricalPrices getHistoricalPrices(String ticker, String startDate, String endDate) {
        log.info("Fetching historical prices for {} from {} to {}", ticker, startDate, endDate);
        // Production: call market data API
        return new HistoricalPrices(ticker, startDate, endDate,
                Map.of(startDate, 140.0, endDate, 155.0));
    }

    @Tool("Get major market index values (S&P 500, NASDAQ, DOW)")
    public MarketIndex getMarketIndex(String indexName) {
        log.info("Fetching market index: {}", indexName);
        return switch (indexName.toUpperCase()) {
            case "SP500", "S&P500" -> new MarketIndex("S&P 500", 4850.43, 0.82);
            case "NASDAQ" -> new MarketIndex("NASDAQ", 15310.97, 1.14);
            case "DOW", "DJIA" -> new MarketIndex("Dow Jones", 37580.65, 0.35);
            default -> new MarketIndex(indexName, 0.0, 0.0);
        };
    }

    @Tool("Calculate volatility (30-day historical volatility) for a given ticker")
    public double getVolatility(String ticker) {
        log.info("Calculating volatility for: {}", ticker);
        // Production: compute from historical price data
        return 0.245;
    }

    public record StockPrice(String ticker, double price, String currency, String timestamp) {}
    public record HistoricalPrices(String ticker, String startDate, String endDate, Map<String, Double> prices) {}
    public record MarketIndex(String name, double value, double changePercent) {}
}
