# AI_GENERATE_START ------
from __future__ import annotations

from qlib.contrib.data.handler import Alpha158
from qlib.contrib.data.loader import Alpha158DL

from stock_trading.market.minute5_summary import MINUTE5_FEATURE_FIELDS

BASIC_FEATURE_FIELDS = (
    "turnover_rate",
    "volume_ratio",
    "log_circulating_market_value",
)


class DailyAlpha158(Alpha158):
    """消费标准日线和同日已发布基础指标的 Alpha158。"""

    def get_feature_config(self):
        fields, names = super().get_feature_config()
        fields.extend(f"${field}" for field in BASIC_FEATURE_FIELDS)
        names.extend(field.upper() for field in BASIC_FEATURE_FIELDS)
        return fields, names


class FullDayMinute5Alpha158(Alpha158):
    """L1 基础指标加完整交易日 5 分钟压缩特征。"""

    def get_feature_config(self):
        fields, names = Alpha158DL.get_feature_config(
            {
                "kbar": {},
                "price": {
                    "windows": [0],
                    "feature": ["OPEN", "HIGH", "LOW", "VWAP"],
                },
                "rolling": {},
            }
        )
        extra_fields = BASIC_FEATURE_FIELDS + MINUTE5_FEATURE_FIELDS
        fields.extend(f"${field}" for field in extra_fields)
        names.extend(field.upper() for field in extra_fields)
        return fields, names


# AI_GENERATE_END ------
