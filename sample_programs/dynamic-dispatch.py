class A(object):
    def call(self: A, x:int):
        print("A")

class B(A):
    def call(self: B, x:int):
        print("B")

class C(B):
    def call(self: C, x:int):
        print("C")

a:A = None
b:A = None
c:A = None
d:A = None

a = A()
b = B()
c = C()

a.call(1)
b.call(1)
c.call(1)
d.call(1)

