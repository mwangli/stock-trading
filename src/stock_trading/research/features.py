# AI_GENERATE_START -
from __future__ import annotations

from qlib.contrib.data.handler import Alpha158
from qlib.contrib.data.loader import Alpha158DL

MORNING_FEATURE_FIELDS = (
    "morning_return",
    "morning_range",
    "morning_close_vwap",
    "morning_last30_return",
)


def morning_alpha158_feature_config() -> tuple[list[str], list[str]]:
    fields, names = Alpha158DL.get_feature_config(
        {
            "kbar": {},
            "price": {"windows": [0], "feature": ["OPEN", "HIGH", "LOW", "VWAP"]},
            "rolling": {},
        }
    )
    fields.extend(f"${field}" for field in MORNING_FEATURE_FIELDS)
    names.extend(field.upper() for field in MORNING_FEATURE_FIELDS)
    return fields, names


class MorningAlpha158(Alpha158):
    """Alpha158 加上午半日量价特征。"""

    def get_feature_config(self):
        return morning_alpha158_feature_config()
# AI_GENERATE_END -
