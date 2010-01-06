class Foo {

  static transients = ['afterDeleteFired']

  boolean afterDeleteFired = false

  def afterDelete() {
    afterDeleteFired = true
  }

}
