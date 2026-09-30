package com.stockflow.inventory;
import java.sql.*;
import org.h2.api.Trigger;
/** Trigger H2 chặn thay đổi lịch sử để test cùng quy tắc bất biến với PostgreSQL. */
public class ImmutableMovementTrigger implements Trigger {
 /** Mọi thao tác cập nhật hoặc xóa bản ghi cũ đều bị từ chối. */
 @Override public void fire(Connection connection, Object[] oldRow, Object[] newRow) throws SQLException {
  throw new SQLException("Không được sửa hoặc xóa lịch sử kiểm toán kho.", "45000");
 }
}
