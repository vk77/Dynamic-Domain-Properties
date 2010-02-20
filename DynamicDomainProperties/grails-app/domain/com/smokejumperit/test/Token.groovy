package com.smokejumperit.test

class Token {

    static constraints = {
    }

  boolean equals(them) {
    if(this.id && this.getClass() == them?.getClass()) {
      return this.id == them.id
    } else {
      return super.equals(them)
    }
  }
}
