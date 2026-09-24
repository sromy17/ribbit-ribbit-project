package com.neueda.leap.trading.repository.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AccountOrderReadMapper {

    @Select("""
        SELECT
            a.accountid AS accountId,
            u.username AS username,
            a.availablefunds AS availableFunds,
            COALESCE(SUM(CASE WHEN tr.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS openOrders,
            COALESCE(SUM(CASE WHEN tr.status = 'EXECUTED' THEN 1 ELSE 0 END), 0) AS executedOrders,
            COALESCE(SUM(CASE WHEN tr.status = 'EXECUTED' THEN tr.bidprice * tr.quantity ELSE 0 END), 0) AS totalExecutedNotional
        FROM accounts a
        JOIN users u ON u.userid = a.userid
        LEFT JOIN trade_request tr ON tr.accountid = a.accountid
        WHERE a.accountid = #{accountId}
        GROUP BY a.accountid, u.username, a.availablefunds
        """)
    AccountOrderSummary getAccountOrderSummary(@Param("accountId") Integer accountId);
}
