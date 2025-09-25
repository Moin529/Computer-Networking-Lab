#include <iostream>
#include <vector>
using namespace std;

void insertionSort(vector <int> &arr)
{
    int n = arr.size();
    for(int i=1; i<n; i++)
    {
        int key = arr[i];
        int j = i - 1;
        while(j>=0 && arr[j] > key)
        {
            arr[j+1] = arr[j];
            j--;
        }
        arr[j + 1] = key;
    }
}

void print(vector <int> &arr)
{
    for(int value:arr)
    {
        cout << value << " ";
    }
    cout << endl;
}

int main()
{
    int n;
    cin >> n;
    vector <int> arr(n);
    for(int i=0; i<n; i++)
    {
        cin >> arr[i];
    }
    insertionSort(arr);
    print(arr);
}