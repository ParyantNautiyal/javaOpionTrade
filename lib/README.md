# Kite Connect JAR File

The Option Chain Generator requires the real Kite Connect Java library to function. Mock data has been completely removed from the application.

## Obtaining the kiteconnect.jar file:

1. Register for a developer account at [Kite Connect](https://kite.trade/)
2. Download the Java library from [Kite Connect Java GitHub repository](https://github.com/zerodhatech/javakiteconnect)
3. Build the library using the instructions in the repository, or download a pre-compiled version
4. Place the JAR file in this directory and rename it to `kiteconnect.jar`

## Important Notice:

- **The application WILL NOT WORK without the real kiteconnect.jar file**
- No mock data or fallbacks are implemented
- If the JAR file is missing, the application will display clear error messages
- All errors related to API connectivity will be logged in `$HOME/.option-chain/logs/option-chain-app.log`

## Troubleshooting:

If you encounter errors like:
- "Real KiteConnect library not found"
- "Failed to load real KiteConnect library"
- "Error getting instruments/quotes"

Ensure that:
1. The JAR file is correctly placed in this directory
2. The JAR file is named exactly `kiteconnect.jar`
3. Your API credentials are valid and properly set
4. You have permission to access the Kite API 