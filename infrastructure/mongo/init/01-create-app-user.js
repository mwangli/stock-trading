// AI_GENERATE_START --
const databaseName = process.env.MONGO_INITDB_DATABASE || "stock_trading";
const username = process.env.MONGO_APP_USER;
const password = process.env.MONGO_APP_PASSWORD;

if (!username || !password) {
    throw new Error("MONGO_APP_USER and MONGO_APP_PASSWORD are required");
}
const applicationDatabase = db.getSiblingDB(databaseName);
const existingUser = applicationDatabase.getUser(username);

if (!existingUser) {
    applicationDatabase.createUser({
        user: username,
        pwd: password,
        roles: [{role: "readWrite", db: databaseName}]
    });
}
// AI_GENERATE_END --
