"""
Simple validation script to verify code structure
This script checks if all required files and classes exist
"""
import sys
import os

def check_file_exists(filepath, description):
    """Check if a file exists"""
    if os.path.exists(filepath):
        print(f"[OK] {description}: {filepath}")
        return True
    else:
        print(f"[MISSING] {description} NOT FOUND: {filepath}")
        return False

def check_module_import(module_path, description):
    """Check if a module can be imported"""
    try:
        __import__(module_path)
        print(f"[OK] {description} can be imported")
        return True
    except ImportError as e:
        print(f"[FAIL] {description} import failed: {e}")
        return False

def main():
    print("=" * 60)
    print("Data Collection Module Validation")
    print("=" * 60)
    
    checks = []
    
    # Check Python files
    print("\n[Python Service Files]")
    python_files = [
        ("app/core/database.py", "Database configuration"),
        ("app/core/config.py", "Application configuration"),
        ("app/services/data_collection_service.py", "Data collection service"),
        ("app/services/scheduler.py", "Task scheduler"),
        ("app/api/data_collection.py", "Data collection API"),
        ("app/api/data_sync.py", "Data sync API"),
        ("app/main.py", "Main application"),
    ]
    
    for filepath, desc in python_files:
        checks.append(check_file_exists(filepath, desc))
    
    # Check test files
    print("\n[Test Files]")
    test_files = [
        ("tests/test_data_collection_service.py", "Unit tests"),
        ("tests/data/test_data.py", "Test data"),
    ]
    
    for filepath, desc in test_files:
        checks.append(check_file_exists(filepath, desc))
    
    # Check Java files
    print("\n[Java Files]")
    java_files = [
        ("../stock-backend/src/main/java/online/mwang/stockTrading/datacollection/client/AKToolsClient.java", "AKTools Client"),
        ("../stock-backend/src/main/java/online/mwang/stockTrading/datacollection/service/StockDataService.java", "Stock Data Service"),
        ("../stock-backend/src/main/java/online/mwang/stockTrading/datacollection/service/impl/StockDataServiceImpl.java", "Stock Data Service Impl"),
        ("../stock-backend/src/main/java/online/mwang/stockTrading/datacollection/scheduler/StockDataSyncJobs.java", "Stock Data Sync Jobs"),
    ]
    
    for filepath, desc in java_files:
        checks.append(check_file_exists(filepath, desc))
    
    # Check documentation
    print("\n[Documentation Files]")
    doc_files = [
        ("../documents/requirements/01-数据采集/数据采集-需求文档.md", "Requirements Document"),
        ("../documents/design/01-数据采集/数据采集服务设计.md", "Design Document"),
        ("../documents/testcases/01-数据采集/数据采集-测试用例.md", "Test Case Document"),
    ]
    
    for filepath, desc in doc_files:
        checks.append(check_file_exists(filepath, desc))
    
    # Summary
    print("\n" + "=" * 60)
    total = len(checks)
    passed = sum(checks)
    print(f"Validation Summary: {passed}/{total} checks passed")
    
    if passed == total:
        print("[SUCCESS] All files are present!")
        return 0
    else:
        print(f"[WARNING] {total - passed} files are missing")
        return 1

if __name__ == "__main__":
    sys.exit(main())
