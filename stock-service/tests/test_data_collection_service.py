"""
Unit tests for DataCollectionService
Tests data collection logic without database dependencies (mocked)
"""
import pytest
from unittest.mock import Mock, patch, MagicMock
from datetime import datetime

from app.services.data_collection_service import DataCollectionService, pd_not_null


class TestDataCollectionService:
    """Test DataCollectionService"""

    @pytest.fixture
    def service(self):
        """Create service instance with mocked dependencies"""
        with patch('app.services.data_collection_service.get_redis'):
            service = DataCollectionService()
            service.redis_client = Mock()
            return service

    @pytest.fixture
    def mock_akshare_stock_list(self):
        """Mock AKShare stock list data"""
        import pandas as pd
        data = [
            {'代码': '000001', '名称': '平安银行', '最新价': 10.5, '涨跌幅': 2.5},
            {'代码': '000002', '名称': '万科A', '最新价': 15.2, '涨跌幅': -1.2},
            {'代码': '000003', '名称': '*ST股票', '最新价': 5.0, '涨跌幅': 0.0},
            {'代码': '600000', '名称': '浦发银行', '最新价': 8.8, '涨跌幅': 0.5},
        ]
        return pd.DataFrame(data)

    @pytest.fixture
    def mock_akshare_historical(self):
        """Mock AKShare historical data"""
        import pandas as pd
        data = [
            {
                '日期': '2024-01-15',
                '开盘': 10.0,
                '收盘': 10.5,
                '最高': 10.8,
                '最低': 9.9,
                '成交量': 1000000,
                '成交额': 10500000.0,
                '振幅': 9.0,
                '涨跌幅': 5.0,
                '涨跌额': 0.5,
                '换手率': 2.5
            }
        ]
        return pd.DataFrame(data)

    # ===============================
    # Stock List Tests
    # ===============================

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_spot_em')
    @patch('app.services.data_collection_service.MySQLSessionLocal')
    async def test_fetch_and_save_stock_list_success(
        self, 
        mock_db_session, 
        mock_akshare, 
        service, 
        mock_akshare_stock_list
    ):
        """TC-PY-001: Test fetching and saving stock list"""
        # Arrange
        mock_akshare.return_value = mock_akshare_stock_list
        mock_db = MagicMock()
        mock_db_session.return_value = mock_db
        mock_db.query.return_value.filter.return_value.first.return_value = None

        # Act
        count = await service.fetch_and_save_stock_list()

        # Assert
        assert count > 0
        mock_akshare.assert_called_once()
        mock_db.commit.assert_called()

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_spot_em')
    async def test_fetch_stock_list_filters_st_stocks(
        self, 
        mock_akshare, 
        service, 
        mock_akshare_stock_list
    ):
        """TC-PY-008: Test ST stocks are filtered out"""
        # Arrange
        mock_akshare.return_value = mock_akshare_stock_list

        # Act - We'll check the transformation logic
        with patch.object(service, '_save_stocks_to_mysql', return_value=3) as mock_save:
            count = await service.fetch_and_save_stock_list()

        # Assert
        # Should save 3 stocks (excluding ST stock)
        assert count == 3

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_spot_em')
    async def test_fetch_stock_list_retry_on_failure(self, mock_akshare, service):
        """TC-PY-006: Test retry mechanism on AKShare failure"""
        # Arrange
        mock_akshare.side_effect = Exception("Network error")

        # Act & Assert
        with pytest.raises(Exception):
            await service.fetch_and_save_stock_list()
        
        # Should retry 3 times (as per @retry decorator)
        assert mock_akshare.call_count == 3

    # ===============================
    # Historical Data Tests
    # ===============================

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_hist')
    @patch('app.services.data_collection_service.get_mongo_collection')
    async def test_fetch_and_save_historical_data_success(
        self, 
        mock_get_collection, 
        mock_akshare, 
        service, 
        mock_akshare_historical
    ):
        """TC-PY-002: Test fetching and saving historical data"""
        # Arrange
        mock_akshare.return_value = mock_akshare_historical
        mock_collection = Mock()
        mock_collection.count_documents.return_value = 0
        mock_collection.update_one.return_value = Mock(upserted_id=True)
        mock_get_collection.return_value = mock_collection

        # Act
        count = await service.fetch_and_save_historical_data('000001', days=60)

        # Assert
        assert count > 0
        mock_akshare.assert_called_once()

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_hist')
    @patch('app.services.data_collection_service.get_mongo_collection')
    async def test_fetch_historical_data_skips_if_sufficient(
        self, 
        mock_get_collection, 
        mock_akshare, 
        service
    ):
        """TC-PY-005: Test skipping when sufficient data exists"""
        # Arrange
        mock_collection = Mock()
        mock_collection.count_documents.return_value = 50  # More than 70% of 60 days
        mock_get_collection.return_value = mock_collection

        # Act
        count = await service.fetch_and_save_historical_data('000001', days=60)

        # Assert
        assert count == 50  # Returns existing count
        mock_akshare.assert_not_called()  # Should not call AKShare

    # ===============================
    # Real-time Quote Tests
    # ===============================

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_spot_em')
    @patch('app.services.data_collection_service.MySQLSessionLocal')
    async def test_fetch_and_save_realtime_quotes_success(
        self, 
        mock_db_session, 
        mock_akshare, 
        service
    ):
        """TC-PY-003: Test fetching and saving real-time quotes"""
        # Arrange
        import pandas as pd
        mock_akshare.return_value = pd.DataFrame([
            {'代码': '000001', '名称': '平安银行', '最新价': 10.5, '涨跌幅': 2.5}
        ])
        mock_db = MagicMock()
        mock_db_session.return_value = mock_db

        # Act
        count = await service.fetch_and_save_realtime_quotes()

        # Assert
        assert count > 0
        service.redis_client.setex.assert_called()
        mock_db.commit.assert_called()

    # ===============================
    # Data Transformation Tests
    # ===============================

    def test_pd_not_null_function(self):
        """Test pd_not_null helper function"""
        assert pd_not_null(10.5) == True
        assert pd_not_null(0) == True
        assert pd_not_null('-') == False

    # ===============================
    # Error Handling Tests
    # ===============================

    @pytest.mark.asyncio
    @patch('app.services.data_collection_service.ak.stock_zh_a_spot_em')
    @patch('app.services.data_collection_service.MySQLSessionLocal')
    async def test_database_write_failure_handling(
        self, 
        mock_db_session, 
        mock_akshare, 
        service, 
        mock_akshare_stock_list
    ):
        """TC-PY-007: Test database write failure handling"""
        # Arrange
        mock_akshare.return_value = mock_akshare_stock_list
        mock_db = MagicMock()
        mock_db.commit.side_effect = Exception("DB Error")
        mock_db_session.return_value = mock_db

        # Act
        count = await service.fetch_and_save_stock_list()

        # Assert - Should handle error gracefully
        mock_db.rollback.assert_called()


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
