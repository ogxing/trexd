rm -rf ./build
mkdir build/db
mkdir build/target/public
boot build-cljs
mv ./target/public/main.js ./build/target/public/
mv ./target/public/index.html ./build/target/public/
boot build-server
mv ./target/* ./build/