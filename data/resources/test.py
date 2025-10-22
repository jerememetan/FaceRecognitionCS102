import onnx
model = onnx.load('./data/resources/arcface.onnx')
print(f"Model has {len(model.graph.output)} output(s):")
for i, output in enumerate(model.graph.output):
    print(f"Output {i}: {output.name}")