rm -rf ../tests/res/referenceTests/*;
cd src;
bazel build //:ResourceTests;
cp bazel-bin/clean-jars/* ../../tests/res/referenceTests;
bazel clean;
cd ../;